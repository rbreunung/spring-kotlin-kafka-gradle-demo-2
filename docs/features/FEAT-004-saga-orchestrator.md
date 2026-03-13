# FEAT-004: Saga Orchestrator — Saga State Machine and Kafka Routing

Status: draft
Date: 2026-03-13
Author: Claude

---

## Context & Motivation

FEAT-003 implements the RiskService with full Kafka integration. However, without a saga orchestrator, no service publishes `RiskCheckRequested` — so the risk pipeline can only be exercised by manually producing Kafka events. The SagaOrchestrator is the central routing engine that drives orders through the full lifecycle.

This feature implements the orchestrator as a stateful saga engine backed by H2 + Spring Data JPA. It fully wires the risk step (OrderPlaced → RiskCheckRequested → RiskApproved/Rejected) and stubs the downstream steps (execution, settlement, notification) so those transitions are handled when the events arrive, but the consumers of those downstream events do not yet exist.

After this feature, placing an order via `POST /orders` on the OrderService produces an observable saga state transition — from `RISK_REQUESTED` through to `RISK_APPROVED` or `RISK_REJECTED` — visible via `GET /sagas/{orderId}` on the saga orchestrator and confirmed by the updated order status via `GET /orders/{id}` on the OrderService.

## Goals

- [ ] Add `ExecutionRequested`, `SettlementRequested`, and `NotificationRequested` events to `:shared`
- [ ] H2 in-memory + Spring Data JPA saga state persistence (`SagaStateEntity`, `SagaStep`, `SagaStateRepository`)
- [ ] Consume `orders` topic: `OrderPlaced` → persist `RISK_REQUESTED` → publish `RiskCheckRequested`; `OrderCancelled` → remove saga state
- [ ] Consume `risk-results` topic: `RiskApproved` → update state to `RISK_APPROVED` → stub-publish `ExecutionRequested`; `RiskRejected` → terminal state `RISK_REJECTED`
- [ ] Consume `executions` topic: `TradeExecuted` → stub handler (log + update state to `EXECUTION_COMPLETE` → stub-publish `SettlementRequested`)
- [ ] Consume `settlements` topic: `PositionSettled` → update state to `SETTLED` → stub-publish `NotificationRequested`; `SettlementFailed` → terminal state `SETTLEMENT_FAILED`
- [ ] Guard all transitions: silently ignore events for unknown or terminal orderIds (idempotent)
- [ ] REST observability: `GET /sagas` and `GET /sagas/{orderId}` on port 8085
- [ ] Integration tests covering each saga step

## Non-Goals

- No compensation or rollback logic for failed steps (deferred to later feature)
- No exactly-once semantics
- No distributed tracing
- No authentication or authorisation
- Full NotificationService integration (stub produces `NotificationRequested`; no consumer implemented yet)

## Architecture

### Saga State Machine

```
[OrderPlaced]         ──▶ RISK_REQUESTED
[RiskApproved]        ──▶ RISK_APPROVED ──▶ EXECUTION_REQUESTED
[RiskRejected]        ──▶ RISK_REJECTED          (terminal)
[TradeExecuted]       ──▶ EXECUTION_COMPLETE ──▶ SETTLEMENT_REQUESTED
[PositionSettled]     ──▶ SETTLED ──▶ (publishes NotificationRequested)  (terminal)
[SettlementFailed]    ──▶ SETTLEMENT_FAILED       (terminal)
[OrderCancelled]      ──▶ (remove state if RISK_REQUESTED; ignore otherwise)
```

Terminal states: `RISK_REJECTED`, `SETTLEMENT_FAILED`, `SETTLED` (after notification stub).

### Component Layout

```
SagaKafkaListener          — @KafkaListener on all consumed topics
      │
      ▼
SagaOrchestrator           — saga step logic; reads/writes SagaStateRepository
      │
      ├── SagaStateRepository (Spring Data JPA → H2)
      │
      └── SagaEventPublisher — KafkaTemplate wrapper; produces to risk-checks,
                                executions (stub), settlements (stub), notifications (stub)

SagaController             — GET /sagas, GET /sagas/{orderId}
      │
      └── SagaStateRepository (read-only queries)
```

### Kafka

**Consumer** (group-id `saga-orchestrator`):

| Topic | Event | Handler |
|---|---|---|
| `orders` | `OrderPlaced` | Start saga: persist `RISK_REQUESTED`, publish `RiskCheckRequested` |
| `orders` | `OrderCancelled` | Remove saga state for orderId (if in `RISK_REQUESTED`; ignore otherwise) |
| `risk-results` | `RiskApproved` | Transition → `RISK_APPROVED` → `EXECUTION_REQUESTED`; stub-publish `ExecutionRequested` |
| `risk-results` | `RiskRejected` | Transition → `RISK_REJECTED` (terminal); log reason |
| `executions` | `TradeExecuted` | Transition → `EXECUTION_COMPLETE` → `SETTLEMENT_REQUESTED`; stub-publish `SettlementRequested` |
| `settlements` | `PositionSettled` | Transition → `SETTLED`; stub-publish `NotificationRequested` |
| `settlements` | `SettlementFailed` | Transition → `SETTLEMENT_FAILED` (terminal); log warning |

`risk-results` and `settlements` carry multiple event types: `@KafkaListener` methods for those topics use `ConsumerRecord<String, Any>` + Kotlin `when (record.value())` dispatch (same pattern as `OrderEventListener` in FEAT-002).

**Producer** (key = `orderId.toString()`):

| Topic | Event | When |
|---|---|---|
| `risk-checks` | `RiskCheckRequested(order)` | On `OrderPlaced` |
| `execution-requests` | `ExecutionRequested(order)` | On `RiskApproved` (stub — no consumer yet) |
| `settlement-requests` | `SettlementRequested(trade, order)` | On `TradeExecuted` (stub — no consumer yet) |
| `notifications` | `NotificationRequested(traderId, orderId, message)` | On `PositionSettled` (stub — no consumer yet) |

### REST API (Observability)

Port 8085. Read-only — no mutations.

| Endpoint | Response | Notes |
|---|---|---|
| `GET /sagas` | `200 List<SagaStateResponse>` | All saga states, ordered by `updatedAt` descending |
| `GET /sagas/{orderId}` | `200 SagaStateResponse` or `404` | Single saga state |

`SagaStateResponse`: `{ orderId, step, tradeId?, updatedAt }`

## Data Model

### New events in `:shared`

```kotlin
data class ExecutionRequested(val order: Order)
data class SettlementRequested(val trade: Trade, val order: Order)
data class NotificationRequested(val traderId: String, val orderId: UUID, val message: String)
```

### `SagaStateEntity` (`:saga-orchestrator` module only)

| Field | Type | Notes |
|---|---|---|
| `orderId` | UUID | PK — the order being orchestrated |
| `step` | String | `SagaStep` enum name stored as VARCHAR |
| `tradeId` | UUID? | Nullable; populated when `TradeExecuted` arrives |
| `updatedAt` | Instant | Updated on every transition |

```kotlin
@Entity
@Table(name = "saga_states")
data class SagaStateEntity(
    @Id val orderId: UUID,
    @Column(nullable = false) val step: String,
    val tradeId: UUID? = null,
    @Column(nullable = false) val updatedAt: Instant
)
```

Store `step` as `entity.step.name` (VARCHAR string) — never use `@Enumerated(EnumType.ORDINAL)`.

### `SagaStep` enum

```kotlin
enum class SagaStep {
    RISK_REQUESTED,
    RISK_APPROVED, RISK_REJECTED,
    EXECUTION_REQUESTED, EXECUTION_COMPLETE,
    SETTLEMENT_REQUESTED,
    SETTLED, SETTLEMENT_FAILED,
    NOTIFICATION_SENT
}
```

### `SagaStateRepository`

```kotlin
interface SagaStateRepository : JpaRepository<SagaStateEntity, UUID> {
    fun findAllByOrderByUpdatedAtDesc(): List<SagaStateEntity>
}
```

## API Surface / Interface

| Interface | Type | Description |
|---|---|---|
| `GET /sagas` | REST | Returns all `SagaStateEntity` records sorted by `updatedAt` desc |
| `GET /sagas/{orderId}` | REST | Returns one saga state or `404` |
| `risk-checks` topic | Kafka producer | Publishes `RiskCheckRequested` |
| `orders` topic | Kafka consumer | Receives `OrderPlaced`, `OrderCancelled` |
| `risk-results` topic | Kafka consumer | Receives `RiskApproved`, `RiskRejected` |
| `execution-requests` topic | Kafka producer | Publishes `ExecutionRequested` (stub) |
| `executions` topic | Kafka consumer | Receives `TradeExecuted` |
| `settlement-requests` topic | Kafka producer | Publishes `SettlementRequested(trade, order)` (stub) |
| `settlements` topic | Kafka consumer | Receives `PositionSettled`, `SettlementFailed` |
| `notifications` topic | Kafka producer | Publishes `NotificationRequested` (stub) |

## Edge Cases & Error Handling

| Scenario | Expected Behavior |
|---|---|
| `RiskApproved` for unknown `orderId` | Log WARN, skip — saga state not found |
| Any event arrives for a terminal-state saga (`RISK_REJECTED`, `SETTLEMENT_FAILED`, `SETTLED`) | Log WARN, skip — idempotency guard |
| `OrderCancelled` arrives but saga is in `RISK_APPROVED` or later | Log WARN, skip — cannot cancel in-flight saga |
| `SettlementFailed` arrives | Log WARN with reason; transition to `SETTLEMENT_FAILED`; no compensation |
| `GET /sagas/{orderId}` for unknown orderId | `404 Not Found` |
| Kafka poison message (malformed JSON) | `ErrorHandlingDeserializer` logs and skips |

## Configuration

### `saga-orchestrator/build.gradle.kts` additions

```kotlin
implementation("org.springframework.boot:spring-boot-starter-web")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("com.h2database:h2")
```

### `saga-orchestrator/src/main/resources/application.yml`

```yaml
server:
  port: 8085

spring:
  datasource:
    url: jdbc:h2:mem:sagadb;DB_CLOSE_DELAY=-1
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
      group-id: saga-orchestrator
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true
```

### `saga-orchestrator/src/test/resources/application.yml`

```yaml
server:
  port: 8085

spring:
  datasource:
    url: jdbc:h2:mem:sagadb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  kafka:
    bootstrap-servers: localhost:9999
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: saga-orchestrator
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true
    listener:
      auto-startup: false
```

## Acceptance Criteria

- [ ] `./gradlew :shared:build` — `ExecutionRequested`, `SettlementRequested`, `NotificationRequested` compile
- [ ] `./gradlew :saga-orchestrator:test` — all tests pass
- [ ] `OrderPlaced` consumed → `SagaStateEntity` persisted with step `RISK_REQUESTED`; `RiskCheckRequested` published to `risk-checks`
- [ ] `OrderCancelled` consumed (in `RISK_REQUESTED` state) → saga state removed
- [ ] `RiskApproved` consumed → state transitions `RISK_REQUESTED → RISK_APPROVED → EXECUTION_REQUESTED`; `ExecutionRequested` published
- [ ] `RiskRejected` consumed → state transitions to `RISK_REJECTED` (terminal); reason logged
- [ ] `TradeExecuted` consumed → state transitions `EXECUTION_REQUESTED → EXECUTION_COMPLETE → SETTLEMENT_REQUESTED`
- [ ] `PositionSettled` consumed → state transitions to `SETTLED`; `NotificationRequested` published
- [ ] `SettlementFailed` consumed → state transitions to `SETTLEMENT_FAILED` (terminal); warning logged
- [ ] Event for unknown orderId → logged and skipped; no exception
- [ ] Event for terminal-state saga → logged and skipped; no state change
- [ ] `GET /sagas` → `200` with list of all saga states
- [ ] `GET /sagas/{orderId}` (known) → `200` with correct saga state
- [ ] `GET /sagas/{orderId}` (unknown) → `404`
- [ ] End-to-end: `docker compose up -d` → `POST /orders` → `GET /sagas/{orderId}` shows `RISK_APPROVED` or `RISK_REJECTED` → `GET /orders/{id}` shows matching status

## Files to Create / Modify

| Path | Action |
|---|---|
| `shared/src/main/kotlin/.../shared/events/ExecutionRequested.kt` | Create |
| `shared/src/main/kotlin/.../shared/events/SettlementRequested.kt` | Create |
| `shared/src/main/kotlin/.../shared/events/NotificationRequested.kt` | Create |
| `saga-orchestrator/build.gradle.kts` | Modify — add `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `h2` |
| `saga-orchestrator/src/main/resources/application.yml` | Modify — add server port, H2/JPA, Kafka config |
| `saga-orchestrator/src/test/resources/application.yml` | Create — sentinel Kafka config |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/domain/SagaStep.kt` | Create |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/domain/SagaStateEntity.kt` | Create |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/repository/SagaStateRepository.kt` | Create |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/kafka/SagaKafkaListener.kt` | Create |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/kafka/SagaEventPublisher.kt` | Create |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/web/SagaController.kt` | Create |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/web/SagaStateResponse.kt` | Create |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/SagaOrchestrator.kt` | Modify — inject listener, publisher, repository; implement step logic |

## Implementation Notes

> Agent: fill this section during feature-impl if implementation differs from spec.

## Related Docs

- [FEAT-003: Risk Service](FEAT-003-risk-service.md)
- [FEAT-002: Order Service](FEAT-002-order-service.md)
- [Architecture](../arch/architecture.md)
- [PLAN-004: Implementation Plan](../plans/PLAN-004-saga-orchestrator.md)
