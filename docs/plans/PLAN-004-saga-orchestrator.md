# PLAN-004: Saga Orchestrator — Implementation Plan

Status: draft
Date: 2026-03-13
Feature: [FEAT-004](../features/FEAT-004-saga-orchestrator.md)

## Progress

> Agent: update after each completed slice. Remove entire section when all slices done.

Current Slice: 4
Completed Slices: [1, 2, 3]
Last Updated: 2026-03-13

## Implementation Review

> Agent: fill this section during the final review step of feature-impl.

Status: pending
Reviewed: —

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| New shared events compile | — | pending |
| `OrderPlaced` → `RISK_REQUESTED` + `RiskCheckRequested` published | — | pending |
| `RiskApproved` → `RISK_APPROVED` → `EXECUTION_REQUESTED` | — | pending |
| `RiskRejected` → `RISK_REJECTED` (terminal) | — | pending |
| `TradeExecuted` → `EXECUTION_COMPLETE` → `SETTLEMENT_REQUESTED` | — | pending |
| `PositionSettled` → `SETTLED` + `NotificationRequested` | — | pending |
| `SettlementFailed` → `SETTLEMENT_FAILED` (terminal) | — | pending |
| Unknown orderId → skip | — | pending |
| Terminal state → skip | — | pending |
| `GET /sagas` → list | — | pending |
| `GET /sagas/{orderId}` → state or 404 | — | pending |
| End-to-end: POST /orders → saga + order status visible | — | pending |

Gaps: —

---

## Open Questions

- [x] Saga state persistence → H2 in-memory + Spring Data JPA
- [x] Observability → REST endpoint on port 8085 (`GET /sagas`, `GET /sagas/{orderId}`)
- [x] Compensation → out of scope (log and terminal state on `SettlementFailed`)
- [x] Idempotency → guard by checking current step before transitioning; ignore unknown/terminal

---

## Vertical Slices

### Slice 1: Shared Events + Saga State Persistence

**What it delivers:** Three new shared events compile. `SagaStateEntity`, `SagaStep`, and `SagaStateRepository` are working with H2 + JPA. The `saga-orchestrator` module has its build configuration updated with web, JPA, and H2 dependencies.

**Files to touch:**
- `shared/.../events/ExecutionRequested.kt` — Create: `data class ExecutionRequested(val order: Order)`
- `shared/.../events/SettlementRequested.kt` — Create: `data class SettlementRequested(val trade: Trade)`
- `shared/.../events/NotificationRequested.kt` — Create: `data class NotificationRequested(val traderId: String, val orderId: UUID, val message: String)`
- `saga-orchestrator/build.gradle.kts` — Modify: add `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `runtimeOnly("com.h2database:h2")`
- `saga-orchestrator/.../domain/SagaStep.kt` — Create: enum with all 9 steps
- `saga-orchestrator/.../domain/SagaStateEntity.kt` — Create: `@Entity @Table(name = "saga_states")` with `orderId` PK, `step` VARCHAR, `tradeId?`, `updatedAt`
- `saga-orchestrator/.../repository/SagaStateRepository.kt` — Create: `JpaRepository<SagaStateEntity, UUID>` + `findAllByOrderByUpdatedAtDesc()`
- `saga-orchestrator/src/main/resources/application.yml` — Modify: add `server.port: 8085`, H2 datasource, JPA `ddl-auto: create-drop`
- `saga-orchestrator/src/test/resources/application.yml` — Create: sentinel Kafka config, H2 config

**Test description:** `@DataJpaTest` — save a `SagaStateEntity` with step `RISK_REQUESTED`, read it back, assert orderId and step match. Save with step `RISK_APPROVED`, assert step stored as string "RISK_APPROVED".

**Status:** [x] done

---

### Slice 2: OrderPlaced → RiskCheckRequested

**What it delivers:** When an `OrderPlaced` event arrives, the orchestrator persists saga state as `RISK_REQUESTED` and publishes `RiskCheckRequested` to `risk-checks`. This is the first active saga step.

**Files to touch:**
- `saga-orchestrator/.../kafka/SagaKafkaListener.kt` — Create: `@KafkaListener` on `orders` topic; `ConsumerRecord<String, Any>` with `when (is OrderPlaced)` dispatch; delegates to `SagaOrchestrator`
- `saga-orchestrator/.../kafka/SagaEventPublisher.kt` — Create: `KafkaTemplate<String, Any>` wrapper; `publishRiskCheckRequested(order)` method
- `saga-orchestrator/.../SagaOrchestrator.kt` — Modify: inject repository + publisher; implement `onOrderPlaced(event)` — persist `RISK_REQUESTED`, publish `RiskCheckRequested`
- `saga-orchestrator/src/main/resources/application.yml` — Modify: add Kafka producer and consumer config

**Test description:** `@EmbeddedKafka` integration test — publish `OrderPlaced`; assert `SagaStateEntity` in DB has step `RISK_REQUESTED`; assert `RiskCheckRequested` arrives on `risk-checks` topic with matching orderId.

**Status:** [x] done

---

### Slice 3: Risk Results — Approved and Rejected

**What it delivers:** `RiskApproved` transitions the saga to `RISK_APPROVED` then immediately to `EXECUTION_REQUESTED` and stub-publishes `ExecutionRequested`. `RiskRejected` transitions to `RISK_REJECTED` (terminal) and logs the reason. Events for unknown or terminal orderIds are silently ignored.

**Files to touch:**
- `saga-orchestrator/.../kafka/SagaKafkaListener.kt` — Modify: add `@KafkaListener` on `risk-results` topic; `ConsumerRecord<String, Any>` with `when (is RiskApproved / is RiskRejected)` dispatch
- `saga-orchestrator/.../kafka/SagaEventPublisher.kt` — Modify: add `publishExecutionRequested(order)` method
- `saga-orchestrator/.../SagaOrchestrator.kt` — Modify: implement `onRiskApproved(event)` and `onRiskRejected(event)`; transition guards (`SagaStateRepository.findById` → check step before transitioning)

**Test description:** `@EmbeddedKafka` integration test:
- Precondition: saga state in DB at `RISK_REQUESTED`
- Publish `RiskApproved` → assert state transitions to `EXECUTION_REQUESTED`; assert `ExecutionRequested` on `executions` topic
- Publish `RiskRejected` (separate precondition) → assert state is `RISK_REJECTED`
- Publish `RiskApproved` for unknown orderId → assert no state created; no exception
- Publish `RiskApproved` when state already `RISK_REJECTED` → assert state unchanged

**Status:** [x] done

---

### Slice 4: Stub Downstream Handlers

**What it delivers:** `TradeExecuted`, `PositionSettled`, and `SettlementFailed` consumers are wired. Stub publishers for `SettlementRequested` and `NotificationRequested` are in place. End of the current saga scope is covered.

**Files to touch:**
- `saga-orchestrator/.../kafka/SagaKafkaListener.kt` — Modify: add `@KafkaListener` on `executions` topic (single-event `TradeExecuted`) and on `settlements` topic (`ConsumerRecord` dispatch)
- `saga-orchestrator/.../kafka/SagaEventPublisher.kt` — Modify: add `publishSettlementRequested(trade)` and `publishNotificationRequested(traderId, orderId, message)` methods
- `saga-orchestrator/.../SagaOrchestrator.kt` — Modify: implement `onTradeExecuted(event)`, `onPositionSettled(event)`, `onSettlementFailed(event)`; update `tradeId` on `SagaStateEntity` when `TradeExecuted` arrives

**Test description:** `@EmbeddedKafka` integration test:
- `TradeExecuted` with precondition `EXECUTION_REQUESTED` → state `SETTLEMENT_REQUESTED`; `SettlementRequested` on `settlements` topic
- `PositionSettled` with precondition `SETTLEMENT_REQUESTED` → state `SETTLED`; `NotificationRequested` on `notifications` topic
- `SettlementFailed` with precondition `SETTLEMENT_REQUESTED` → state `SETTLEMENT_FAILED`; warning logged

**Status:** [ ] todo

---

### Slice 5: REST Observability

**What it delivers:** `GET /sagas` returns all saga states sorted by `updatedAt` desc. `GET /sagas/{orderId}` returns one record or `404`. The application context loads with the web layer.

**Files to touch:**
- `saga-orchestrator/.../web/SagaStateResponse.kt` — Create: `data class SagaStateResponse(val orderId: UUID, val step: String, val tradeId: UUID?, val updatedAt: Instant)`
- `saga-orchestrator/.../web/SagaController.kt` — Create: `@RestController` with `@GetMapping("/sagas")` and `@GetMapping("/sagas/{orderId}")`; injects `SagaStateRepository`

**Test description:** `@WebMvcTest(SagaController::class)` with mocked `SagaStateRepository`:
- `GET /sagas` → `200` with list of responses
- `GET /sagas/{orderId}` for known id → `200` with correct state
- `GET /sagas/{orderId}` for unknown id → `404`

**Status:** [ ] todo

---

### Slice 6: End-to-End Verification

**What it delivers:** With all services running (`docker compose up -d` + JVM services), placing an order via `POST /orders` produces a complete, observable saga progression through the risk step.

**Files to touch:**
- No new files — this slice is a manual verification and documentation of the end-to-end flow

**Test description (manual):**
1. `docker compose up -d` — Kafka on `localhost:9092`
2. Start `saga-orchestrator` (port 8085) and `risk-service`
3. Start `order-service` (port 8080)
4. `POST http://localhost:8080/orders` with `{ "traderId": "demo", "symbol": "AAPL", "quantity": 100, "side": "BUY" }`
5. Note returned `orderId`
6. `GET http://localhost:8085/sagas/{orderId}` → step should be `RISK_APPROVED` (or `RISK_REJECTED` if risk.simulate-failure-probability > 0)
7. `GET http://localhost:8080/orders/{orderId}` → status should be `RISK_APPROVED` (or `RISK_REJECTED`)
8. Test with `quantity: 15000` → `RiskRejected("quantity-exceeds-limit")` in both services

**Status:** [ ] todo
