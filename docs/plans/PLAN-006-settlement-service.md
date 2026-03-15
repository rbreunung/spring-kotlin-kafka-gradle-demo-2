# PLAN-006: Settlement Service — Implementation Plan

Status: complete
Date: 2026-03-13
Feature: [FEAT-006](../features/FEAT-006-settlement-service.md)

## Progress

> Agent: update after each completed slice. Remove entire section when all slices done.

Current Slice: —
Completed Slices: [1, 2, 3, 4, 5, 6]
Last Updated: 2026-03-15

## Implementation Review

> Agent: fill this section during the final review step of feature-impl.

Status: complete
Reviewed: 2026-03-15

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| BUY settled → `PositionSettled` + position created | `SettlementEventPublisherTest` + `PositionPersistenceTest` | ✅ |
| Accumulated BUY → avgCost recalculated | `PositionPersistenceTest.BUY 100 at 100 then BUY 50 at 120` | ✅ |
| SELL settled → quantity decremented | `PositionPersistenceTest.SELL 30 from position of 150` | ✅ |
| 3 retries exhausted → `SettlementFailed` | `RetryFallbackTest` | ✅ |
| Bulkhead overflow → `SettlementFailed("bulkhead-full")` | `BulkheadFallbackTest` | ✅ |
| Poison message → `dlq.settlements` | `DeadLetterTopicTest` | ✅ |

Gaps: none

---

## Open Questions

- [x] Position persistence → H2 in-memory + Spring Data JPA
- [x] Failure simulation → configurable probability (`settlement.simulate-failure-probability`)
- [x] Retry config → 3 attempts, exponential backoff (1s, 2s, 4s)
- [x] Bulkhead config → max 5 concurrent calls, max wait 100ms
- [x] DLT → `dlq.settlements` via `DeadLetterPublishingRecoverer`
- [x] Short selling → allowed (negative quantity), no validation

---

## Vertical Slices

### Slice 1: Position Persistence

**What it delivers:** `PositionEntity`, `PositionRepository`, and `SettlementException` are in place with H2 + JPA config. BUY and SELL position update logic is implemented and unit-tested.

**Files to touch:**
- `settlement/build.gradle.kts` — Modify: add `implementation("org.springframework.boot:spring-boot-starter-data-jpa")` + `runtimeOnly("com.h2database:h2")`
- `settlement/src/main/kotlin/.../settlement/domain/PositionEntity.kt` — Create: `@Entity @Table(name = "positions", uniqueConstraints = ...)` with `traderId`, `symbol`, `quantity`, `avgCost`, `updatedAt`; surrogate Long PK
- `settlement/src/main/kotlin/.../settlement/repository/PositionRepository.kt` — Create: `JpaRepository<PositionEntity, Long>` + `findByTraderIdAndSymbol()`
- `settlement/src/main/kotlin/.../settlement/exception/SettlementException.kt` — Create: `class SettlementException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)`
- `settlement/src/main/kotlin/.../settlement/SettlementService.kt` — Modify: inject `PositionRepository`; implement `updatePosition(trade, order)` with BUY/SELL logic and avgCost calculation
- `settlement/src/main/resources/application.yml` — Modify: add H2 datasource + JPA config
- `settlement/src/test/resources/application.yml` — Create: H2 + sentinel Kafka config

**Test description:** `@DataJpaTest`:
- BUY 100 shares at 100.00 → position: qty=100, avgCost=100.00
- BUY 50 more at 120.00 → position: qty=150, avgCost=106.67 (weighted average)
- SELL 30 shares → position: qty=120, avgCost=106.67 (unchanged)
- SELL with no existing position → short position: qty=-30

**Status:** [x] complete

---

### Slice 2: Kafka Consumer

**What it delivers:** `SettlementKafkaListener` consumes `SettlementRequested` from `settlement-requests` and delegates to `SettlementService`. Application context loads with Kafka consumer config.

**Files to touch:**
- `settlement/src/main/kotlin/.../settlement/kafka/SettlementKafkaListener.kt` — Create: `@KafkaListener(topics = ["settlement-requests"])` with `SettlementRequested` parameter; delegates to `SettlementService.settle(trade, order)`
- `settlement/src/main/kotlin/.../settlement/SettlementService.kt` — Modify: add `settle(trade, order)` entry point
- `settlement/src/main/resources/application.yml` — Modify: add Kafka consumer config (group-id `settlement-service`, `JsonDeserializer` + `ErrorHandlingDeserializer`, trusted packages, type headers)

**Test description:** `@EmbeddedKafka` integration test — publish one `SettlementRequested`; verify `SettlementService.settle()` is called. Poison message: publish malformed JSON; verify `ErrorHandlingDeserializer` handles it (no uncaught exception).

**Status:** [x] complete

---

### Slice 3: Kafka Producer

**What it delivers:** `SettlementEventPublisher` publishes `PositionSettled` or `SettlementFailed` to `settlements`. On success, position is updated and `PositionSettled` is published with the current `PositionEntity` converted to `Position`.

**Files to touch:**
- `settlement/src/main/kotlin/.../settlement/kafka/SettlementEventPublisher.kt` — Create: `KafkaTemplate<String, Any>` wrapper; `publishPositionSettled(tradeId, position)` and `publishSettlementFailed(tradeId, reason)`; key = `trade.id.toString()`, topic = `settlements`
- `settlement/src/main/kotlin/.../settlement/SettlementService.kt` — Modify: inject `SettlementEventPublisher`; call publisher after position update
- `settlement/src/main/resources/application.yml` — Modify: add Kafka producer config (`JsonSerializer`)

**Test description:** `@EmbeddedKafka` integration test — publish `SettlementRequested` for a BUY order; consume from `settlements`; assert `PositionSettled` arrives with `tradeId == trade.id` and `position.quantity == order.quantity`.

**Status:** [x] complete

---

### Slice 4: Resilience4j Retry

**What it delivers:** `SettlementService.settle()` is protected by `@Retry(name = "settlementOperation")` with a retry fallback that publishes `SettlementFailed`. `SettlementExternalStub.execute()` simulates failures based on `settlement.simulate-failure-probability`.

**Files to touch:**
- `settlement/src/main/kotlin/.../settlement/SettlementService.kt` — Modify: add `@Retry(name = "settlementOperation", fallbackMethod = "settleFallback")` to `settle()`; add `settleFallback(trade, order, ex)` publishing `SettlementFailed(trade.id, ex.message)`; inject `@Value("\${settlement.simulate-failure-probability}") val failureProbability: Double`; throw `SettlementException` based on probability before updating position
- `settlement/src/main/resources/application.yml` — Modify: add `resilience4j.retry.instances.settlementOperation` config block

**Test description:** `@EmbeddedKafka` + `@SpringBootTest`:
1. Set `settlement.simulate-failure-probability=1.0`
2. Publish `SettlementRequested`
3. Consume from `settlements`; assert `SettlementFailed` arrives
4. Assert `SettlementFailed.tradeId == trade.id`
5. Assert position in DB not updated
6. Assert logs show 3 attempt entries (via log capture or attempt counter)

**Status:** [x] complete

---

### Slice 5: Resilience4j Bulkhead

**What it delivers:** `SettlementService.settle()` is also protected by `@Bulkhead(name = "settlementOperation")` (ThreadPool type). When the limit is exceeded, `BulkheadFullException` triggers a fallback that publishes `SettlementFailed("bulkhead-full")`.

**Files to touch:**
- `settlement/src/main/kotlin/.../settlement/SettlementService.kt` — Modify: add `@Bulkhead(name = "settlementOperation", fallbackMethod = "settleBulkheadFallback")` (outer annotation); add `settleBulkheadFallback(trade, order, ex)` publishing `SettlementFailed(trade.id, "bulkhead-full")`
- `settlement/src/main/resources/application.yml` — Modify: add `resilience4j.bulkhead.instances.settlementOperation` config block (ThreadPool type)

**Test description:** `@EmbeddedKafka` + `@SpringBootTest`:
1. Set a slow settlement operation (add 500ms sleep to `SettlementExternalStub.execute()` only in this test via `@TestPropertySource` or mock)
2. Publish 6 concurrent `SettlementRequested` events
3. Consume from `settlements`; assert at least 1 `SettlementFailed("bulkhead-full")` arrives
4. Assert other settlements succeed (`PositionSettled`)

**Status:** [x] complete

---

### Slice 6: Dead Letter Topic

**What it delivers:** Deserialization failures and unrecoverable Kafka errors route to `dlq.settlements` via Spring Kafka's `DeadLetterPublishingRecoverer`. The consumer factory is configured with a `DefaultErrorHandler` using the recoverer.

**Files to touch:**
- `settlement/src/main/kotlin/.../settlement/kafka/SettlementKafkaConfig.kt` — Create: `@Configuration` class defining a `KafkaListenerContainerFactory` bean that wires `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` targeting `dlq.settlements`
- `settlement/src/main/kotlin/.../settlement/kafka/SettlementKafkaListener.kt` — Modify: ensure listener uses the custom container factory if not already the default

**Test description:** `@EmbeddedKafka` + `@SpringBootTest`:
1. Publish a message with malformed JSON to `settlement-requests`
2. Consume from `dlq.settlements`; assert the message lands there
3. Assert the main `settlements` topic receives nothing

**Status:** [x] complete
