# FEAT-006: Settlement Service — Position Persistence and Resilience4j Retry and Bulkhead

Status: complete
Date: 2026-03-13
Author: Claude

---

## Context & Motivation

After FEAT-005 implements the ExecutionService, orders can progress to `EXECUTED` status. The SagaOrchestrator (FEAT-004) stub-publishes `SettlementRequested` after `TradeExecuted` arrives, but without a real SettlementService consumer the saga stalls at `SETTLEMENT_REQUESTED`.

This feature implements the SettlementService: it consumes `SettlementRequested`, updates a per-trader position in H2 (tracking quantity and average cost), and publishes `PositionSettled` or `SettlementFailed`. It also demonstrates the two remaining Resilience4j patterns specified in the architecture: Retry (with exponential backoff) and Bulkhead (ThreadPool), plus a Dead Letter Topic for unrecoverable failures.

After this feature, orders flow from `EXECUTED` through to `SETTLED` status — completing all major saga steps. Only the NotificationService remains as the final confirmation step.

## Goals

- [x] Kafka consumer on `settlement-requests` topic: consume `SettlementRequested(trade: Trade, order: Order)`
- [x] H2 in-memory + Spring Data JPA position persistence: `PositionEntity(traderId, symbol, quantity, avgCost)` — one row per trader+symbol pair, upserted on each trade
  - BUY: `newQty = existingQty + order.quantity`; `newAvgCost = (existingQty × existingAvgCost + order.quantity × trade.executedPrice) / newQty`
  - SELL: `newQty = existingQty - order.quantity`; `avgCost` unchanged; initial SELL with no position creates a short position (negative quantity)
- [x] Simulated failure: configurable `settlement.simulate-failure-probability` (default `0.0`); when triggered, throw `SettlementException` before updating position
- [x] Resilience4j Retry: 3 attempts, exponential backoff (initial wait 1s, multiplier 2), wrapping the settlement operation
- [x] Retry fallback (all 3 attempts failed): publish `SettlementFailed(tradeId, reason)` to `settlements` topic; position not updated
- [x] Resilience4j Bulkhead (ThreadPool): max 5 concurrent settlement calls, max wait 100ms; overflow → `BulkheadFullException` → publish `SettlementFailed`
- [x] DLT: Spring Kafka `DeadLetterPublishingRecoverer` on `dlq.settlements` for deserialization errors and unrecoverable Kafka errors
- [x] Kafka producer on `settlements` topic: publish `PositionSettled(tradeId, position)` on success
- [x] Integration tests with embedded Kafka covering success path, retry exhaustion, and DLT routing

## Non-Goals

- No REST API (Kafka-only service)
- No real position database (H2 in-memory sufficient)
- No authentication or authorisation
- No idempotency / dedup (same pattern as FEAT-003: downstream guards handle duplicates)
- No fractional share quantities
- Oversell / negative quantity: allowed (short position); no validation

## Architecture

### Component Design

```
settlement-requests topic
         │
         ▼
SettlementKafkaListener
         │  (SettlementRequested)
         ▼
SettlementService.settle(trade, order)   ← @Bulkhead + @Retry
         │
         ├── SettlementExternalStub.execute(trade, order)  ← may throw SettlementException
         │       (configurable failure probability)
         │
         ├── (success) → upsert PositionEntity → PositionSettled
         └── (retry exhausted / bulkhead full) → SettlementFailed
                   │
                   ▼
SettlementEventPublisher
         │
         ├── settlements topic → PositionSettled(tradeId, position)
         │                    or SettlementFailed(tradeId, reason)
         │
         └── dlq.settlements topic ← Spring Kafka DLT (deserialization failures)
```

### Resilience4j Configuration

Two patterns are applied to `SettlementService.settle()`:

**Retry** (inner — applied first):
- `maxAttempts: 3`
- `waitDuration: 1s`
- `enableExponentialBackoff: true`
- `exponentialBackoffMultiplier: 2`
- Retries on `SettlementException`
- Fallback method: publishes `SettlementFailed` with reason from last exception

**Bulkhead** (outer — applied around retry):
- `maxConcurrentCalls: 5`
- `maxWaitDuration: 100ms`
- Overflow: `BulkheadFullException` → fallback publishes `SettlementFailed("bulkhead-full")`

### Kafka

**Consumer** (group-id `settlement-service`):

| Topic | Event | Handler |
|---|---|---|
| `settlement-requests` | `SettlementRequested(trade, order)` | `SettlementKafkaListener.onSettlementRequested()` |

**Producer** (key = `trade.id.toString()`):

| Topic | Event | Trigger |
|---|---|---|
| `settlements` | `PositionSettled(tradeId, position)` | Settlement succeeds, position updated |
| `settlements` | `SettlementFailed(tradeId, reason)` | All retries exhausted or bulkhead full |
| `dlq.settlements` | Original message (raw bytes) | Deserialization failure or unrecoverable error |

Deserialization uses the same pattern as previous services: `JsonDeserializer` + `ErrorHandlingDeserializer`. The `DeadLetterPublishingRecoverer` is configured as the recovery handler for `ErrorHandlingDeserializer`.

## Data Model

### `PositionEntity` (`:settlement` module only)

| Field | Type | Notes |
|---|---|---|
| `id` | Long | Auto-generated PK (surrogate) |
| `traderId` | String | Part of unique constraint with `symbol` |
| `symbol` | String | Part of unique constraint with `traderId` |
| `quantity` | Int | Running net quantity; negative = short position |
| `avgCost` | BigDecimal | Weighted average cost of current long position |
| `updatedAt` | Instant | Updated on every trade |

Unique constraint: `(traderId, symbol)` — enforced via `@Table(uniqueConstraints = ...)`.

```kotlin
@Entity
@Table(
    name = "positions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["trader_id", "symbol"])]
)
data class PositionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @Column(name = "trader_id", nullable = false) val traderId: String,
    @Column(nullable = false) val symbol: String,
    @Column(nullable = false) var quantity: Int,
    @Column(nullable = false) var avgCost: BigDecimal,
    @Column(nullable = false) var updatedAt: Instant
)
```

### `PositionRepository`

```kotlin
interface PositionRepository : JpaRepository<PositionEntity, Long> {
    fun findByTraderIdAndSymbol(traderId: String, symbol: String): PositionEntity?
}
```

### Position Update Logic

```
val existing = positionRepository.findByTraderIdAndSymbol(traderId, symbol)
               ?: PositionEntity(traderId, symbol, 0, BigDecimal.ZERO, Instant.now())

if (order.side == Side.BUY) {
    val newQty = existing.quantity + order.quantity
    val newAvgCost = if (newQty > 0)
        (existing.quantity.toBigDecimal() * existing.avgCost + order.quantity.toBigDecimal() * trade.executedPrice) / newQty.toBigDecimal()
    else BigDecimal.ZERO
    existing.quantity = newQty
    existing.avgCost = newAvgCost.setScale(4, RoundingMode.HALF_UP)
} else { // SELL
    existing.quantity -= order.quantity
    // avgCost unchanged for SELL
}

existing.updatedAt = Instant.now()
positionRepository.save(existing)
```

## API Surface / Interface

| Interface | Type | Description |
|---|---|---|
| `settlement-requests` topic | Kafka consumer | Receives `SettlementRequested(trade, order)` |
| `settlements` topic | Kafka producer | Publishes `PositionSettled` or `SettlementFailed` |
| `dlq.settlements` topic | Kafka producer (DLT) | Receives deserialization/unrecoverable failures |
| `settlement.simulate-failure-probability` | Spring property | `Double` in `[0.0, 1.0]`; probability of `SettlementException`; default `0.0` |

## Edge Cases & Error Handling

| Scenario | Expected Behavior |
|---|---|
| BUY trade, no existing position | Create new `PositionEntity` with `quantity = order.quantity`, `avgCost = trade.executedPrice` |
| SELL trade, no existing position | Create short position: `quantity = -order.quantity`, `avgCost = 0.00` |
| `simulate-failure-probability > 0`, attempt 1 fails | Retry waits 1s, attempts again |
| All 3 retry attempts fail | Retry fallback: `SettlementFailed(tradeId, lastException.message)` published; position not updated |
| 6 concurrent settlement calls (bulkhead max=5) | 6th call: `BulkheadFullException` → `SettlementFailed(tradeId, "bulkhead-full")` |
| Kafka deserialization fails | `ErrorHandlingDeserializer` routes to `DeadLetterPublishingRecoverer` → `dlq.settlements` |

## Configuration

### `settlement/build.gradle.kts` additions

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("com.h2database:h2")
// spring-cloud-starter-circuitbreaker-resilience4j already present — provides Retry + Bulkhead
```

### `settlement/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:settlementdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: settlement-service
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true

settlement:
  simulate-failure-probability: 0.0

resilience4j:
  retry:
    instances:
      settlementOperation:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - de.antrophos.demo.spring.kafka.trader.settlement.exception.SettlementException
  bulkhead:
    instances:
      settlementOperation:
        maxConcurrentCalls: 5
        maxWaitDuration: 100ms
```

### `settlement/src/test/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:settlementdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  kafka:
    bootstrap-servers: localhost:9999
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: settlement-service
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true
    listener:
      auto-startup: false

settlement:
  simulate-failure-probability: 0.0
```

## Acceptance Criteria

- [x] `./gradlew :settlement:test` — all tests pass
- [x] BUY order settled → `PositionSettled` published; `PositionEntity` in DB has `quantity = order.quantity`, `avgCost = executedPrice` (new position)
- [x] BUY order settled again (same trader+symbol) → position quantity accumulated; `avgCost` recalculated as weighted average
- [x] SELL order settled → `PositionSettled` published; position quantity decremented
- [x] With `simulate-failure-probability=1.0`: single `SettlementRequested` → 3 retry attempts (observable via logs or attempt counter) → `SettlementFailed` published on `settlements` topic; position not modified
- [x] Bulkhead overflow (6 concurrent with max=5) → 6th → `SettlementFailed("bulkhead-full")`
- [x] Kafka poison message (malformed JSON) → routed to `dlq.settlements` via DLT
- [x] `SettlementFailed.tradeId` matches `trade.id`
- [x] `PositionSettled.position` reflects post-trade state

## Files to Create / Modify

| Path | Action |
|---|---|
| `settlement/build.gradle.kts` | Modify — add `spring-boot-starter-data-jpa`, `h2` |
| `settlement/src/main/resources/application.yml` | Modify — add H2/JPA, Kafka, Resilience4j config |
| `settlement/src/test/resources/application.yml` | Create — sentinel Kafka config |
| `settlement/src/main/kotlin/.../settlement/domain/PositionEntity.kt` | Create |
| `settlement/src/main/kotlin/.../settlement/repository/PositionRepository.kt` | Create |
| `settlement/src/main/kotlin/.../settlement/exception/SettlementException.kt` | Create |
| `settlement/src/main/kotlin/.../settlement/kafka/SettlementKafkaListener.kt` | Create |
| `settlement/src/main/kotlin/.../settlement/kafka/SettlementEventPublisher.kt` | Create |
| `settlement/src/main/kotlin/.../settlement/SettlementService.kt` | Modify — add Retry + Bulkhead + position logic |

## Implementation Notes

**Bulkhead type: Semaphore (not ThreadPool)**
The spec specified `Resilience4j Bulkhead (ThreadPool)`. The implementation uses the standard Semaphore-based bulkhead (`io.github.resilience4j:resilience4j-bulkhead`), configured via `resilience4j.bulkhead.instances.settlementOperation`. The semaphore bulkhead enforces the same `max-concurrent-calls=5` constraint and produces the same `BulkheadFullException` fallback behaviour. ThreadPool bulkhead would require `resilience4j.thread-pool-bulkhead` config; the semaphore approach is simpler and sufficient for this demo.

**`artificial-delay-ms` property added**
A `settlement.artificial-delay-ms` property (default `0`) was added to `SettlementService` to support deterministic bulkhead concurrency testing without fragile thread timing.

## Related Docs

- [FEAT-005: Execution Service](FEAT-005-execution-service.md)
- [FEAT-004: Saga Orchestrator](FEAT-004-saga-orchestrator.md)
- [Architecture](../arch/architecture.md)
- [PLAN-006: Implementation Plan](../plans/PLAN-006-settlement-service.md)
