# PLAN-008: Saga Compensation — Rollback on Settlement Failure — Implementation Plan

Status: complete
Date: 2026-03-16
Feature: [FEAT-008](../features/FEAT-008-saga-compensation.md)

## Progress

Current Slice: 1
Completed Slices: []
Last Updated: 2026-03-16

## Implementation Review

Status: pending
Reviewed: —

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `CompensationRequested` and `TradeVoided` compile | `./gradlew :shared:build` | pending |
| `TradeEntity` persisted on `TradeExecuted`; VOIDED on `CompensationRequested` | `ExecutionCompensationIntegrationTest` | pending |
| `SettlementFailed` → `SETTLEMENT_FAILED` → `COMPENSATION_REQUESTED`; `CompensationRequested` published | `SagaOrchestratorCompensationTest` | pending |
| `TradeVoided` → `COMPENSATION_COMPLETE` in saga | `SagaOrchestratorCompensationTest` | pending |
| `SettlementFailed` → order `COMPENSATION_IN_PROGRESS`; `TradeVoided` → order `COMPENSATION_COMPLETE` | `OrderEventListenerCompensationTest` | pending |
| E2E: saga + order both reach `COMPENSATION_COMPLETE` | `SagaCompensationTest` | pending |
| Idempotency: duplicate `SettlementFailed` skipped | `SagaOrchestratorCompensationTest` | pending |

Gaps: —

---

## Open Questions

> All resolved during spec session.

---

## Vertical Slices

Ordered core-domain outward: shared events → execution persistence → compensation flow
→ saga orchestrator → order service → system test.

---

### Slice 1: Shared Events

**What it delivers:** `CompensationRequested` and `TradeVoided` are available as
shared event types, trusted by all consumer services.

**Files to touch:**
- `shared/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/shared/events/CompensationRequested.kt` — Create: `data class CompensationRequested(val orderId: UUID, val tradeId: UUID, val reason: String)`
- `shared/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/shared/events/TradeVoided.kt` — Create: `data class TradeVoided(val tradeId: UUID, val orderId: UUID)`

**Test description:** `./gradlew :shared:build` passes. Both classes are importable
from other modules via the `:shared` dependency.

**Status:** [ ] todo

---

### Slice 2: ExecutionService — Trade Persistence

**What it delivers:** Every executed trade is persisted as a `TradeEntity` with
status `EXECUTED`. `TradeRepository.findById(tradeId)` works.

**Files to touch:**
- `execution/build.gradle.kts` — add `spring-boot-starter-data-jpa` + `runtimeOnly("com.h2database:h2")`
- `execution/src/main/resources/application.yml` — add `spring.datasource` + `spring.jpa` config (H2 in-memory, `ddl-auto: create-drop`, `show-sql: false`)
- `execution/src/test/resources/application.yml` — Create: sentinel Kafka (`localhost:9999`, `auto-startup: false`) + same datasource config
- `execution/src/main/kotlin/.../execution/domain/TradeStatus.kt` — Create enum: `EXECUTED, VOIDED`
- `execution/src/main/kotlin/.../execution/domain/TradeEntity.kt` — Create `@Entity @Table(name="trades")` with fields: `id: UUID` (@Id), `orderId: UUID`, `executedPrice: BigDecimal`, `executedAt: Instant`, `status: String`
- `execution/src/main/kotlin/.../execution/repository/TradeRepository.kt` — Create `interface TradeRepository : JpaRepository<TradeEntity, UUID>`
- `execution/src/main/kotlin/.../execution/ExecutionService.kt` — Inject `TradeRepository`; after creating `Trade`, also call `tradeRepository.save(TradeEntity(id=trade.id, orderId=trade.orderId, executedPrice=trade.executedPrice, executedAt=trade.executedAt))` before publishing

**Test description:** Unit test `ExecutionServiceTest` — mock `TradeRepository`; call
`execute(order)`; verify `tradeRepository.save()` called with matching `TradeEntity`.
Integration test verifies `TradeEntity` exists in H2 after `TradeExecuted` is published.

**Status:** [ ] todo

---

### Slice 3: ExecutionService — Compensation Path

**What it delivers:** `ExecutionService` can receive `CompensationRequested`, mark
the trade `VOIDED`, and publish `TradeVoided`.

**Files to touch:**
- `execution/src/main/kotlin/.../execution/ExecutionCompensationService.kt` — Create `@Service`; inject `TradeRepository` + `ExecutionEventPublisher`; method `voidTrade(tradeId: UUID, orderId: UUID)`: find entity, mark VOIDED (or log WARN if not found), save, publish `TradeVoided`
- `execution/src/main/kotlin/.../execution/kafka/CompensationKafkaListener.kt` — Create `@KafkaListener(topics = ["compensation-requests"])`; call `executionCompensationService.voidTrade(event.tradeId, event.orderId)`
- `execution/src/main/kotlin/.../execution/kafka/ExecutionEventPublisher.kt` — Add `publishTradeVoided(tradeId: UUID, orderId: UUID)`: `kafkaTemplate.send("compensation-results", tradeId.toString(), TradeVoided(tradeId, orderId))`

**Test description:** Integration test `ExecutionCompensationIntegrationTest` (embedded
Kafka): produce `CompensationRequested`; verify `TradeEntity.status = VOIDED` in DB;
verify `TradeVoided` published on `compensation-results`. Test unknown `tradeId`: verify
`TradeVoided` still published + WARN logged.

**Status:** [ ] todo

---

### Slice 4: SagaOrchestrator — Compensation Steps

**What it delivers:** `SagaOrchestrator` transitions through
`SETTLEMENT_FAILED → COMPENSATION_REQUESTED` on `SettlementFailed`, and through
`COMPENSATION_REQUESTED → COMPENSATION_COMPLETE` on `TradeVoided`.

**Files to touch:**
- `saga-orchestrator/src/main/kotlin/.../saga/domain/SagaStep.kt` — Add `COMPENSATION_REQUESTED`, `COMPENSATION_COMPLETE`, `COMPENSATION_FAILED`; update `isTerminal`: `setOf(RISK_REJECTED, SETTLED, COMPENSATION_COMPLETE, COMPENSATION_FAILED)` (remove `SETTLEMENT_FAILED`)
- `saga-orchestrator/src/main/kotlin/.../saga/SagaOrchestrator.kt` — Modify `onSettlementFailed`: add step guard (`SETTLEMENT_REQUESTED` only), null-check `entity.tradeId`, two saves, publish `CompensationRequested`; add new `onTradeVoided(event: TradeVoided)`: resolve by tradeId, guard `COMPENSATION_REQUESTED`, save `COMPENSATION_COMPLETE`
- `saga-orchestrator/src/main/kotlin/.../saga/kafka/SagaKafkaListener.kt` — Add `@KafkaListener(topics = ["compensation-results"])` for `TradeVoided`
- `saga-orchestrator/src/main/kotlin/.../saga/kafka/SagaEventPublisher.kt` — Add `publishCompensationRequested(orderId: UUID, tradeId: UUID, reason: String)`: `kafkaTemplate.send("compensation-requests", orderId.toString(), CompensationRequested(orderId, tradeId, reason))`

**Test description:** Integration test `SagaOrchestratorCompensationTest` (embedded
Kafka + `@DataJpaTest`-style): simulate `SettlementFailed` → assert saga entity has
step `COMPENSATION_REQUESTED`; assert `CompensationRequested` published. Simulate
`TradeVoided` → assert saga step `COMPENSATION_COMPLETE`. Duplicate `SettlementFailed`
after `COMPENSATION_REQUESTED` → skipped (step guard).

**Status:** [ ] todo

---

### Slice 5: OrderService — Compensation Status

**What it delivers:** `OrderService` tracks compensation in order status:
`SettlementFailed` → `COMPENSATION_IN_PROGRESS`; `TradeVoided` → `COMPENSATION_COMPLETE`.

**Files to touch:**
- `order/src/main/kotlin/.../order/domain/OrderStatus.kt` — Add `COMPENSATION_IN_PROGRESS` (non-terminal) and `COMPENSATION_COMPLETE` (terminal); update `isTerminal` to include `COMPENSATION_COMPLETE`
- `order/src/main/kotlin/.../order/kafka/OrderEventListener.kt` — Change `onSettlement` `SettlementFailed` branch: `applyTransition(entity.id, OrderStatus.COMPENSATION_IN_PROGRESS, fromStatus = OrderStatus.EXECUTED)`; add new `@KafkaListener(topics = ["compensation-results"])` method `onCompensationResult`: find order by `event.orderId`, call `applyTransition(orderId, OrderStatus.COMPENSATION_COMPLETE, fromStatus = OrderStatus.COMPENSATION_IN_PROGRESS)`

**Test description:** Unit test `OrderEventListenerCompensationTest`: `SettlementFailed`
message → verify `applyTransition` called with `COMPENSATION_IN_PROGRESS`. `TradeVoided`
message → verify `applyTransition` called with `COMPENSATION_COMPLETE`.

Note: `onCompensationResult` uses `event.orderId` from `TradeVoided` directly (no
`findByTradeId` needed — `orderId` is in the event).

**Status:** [ ] todo

---

### Slice 6: System Test — End-to-End Compensation

**What it delivers:** Full end-to-end verification: order placed → settlement fails
→ compensation runs → order and saga both reach `COMPENSATION_COMPLETE`.

**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/SagaCompensationTest.kt` — Create; use `@DynamicPropertySource` to set `settlement.simulate-failure-probability=1.0`; place order via `POST /orders`; poll `GET /sagas/{orderId}` until step = `COMPENSATION_COMPLETE` (timeout 60s); poll `GET /orders/{id}` until status = `COMPENSATION_COMPLETE`; assert both match

**Test description:** E2E via Testcontainers `DockerCompose`: all services up with
failure probability = 1.0 for settlement service. Place a BUY order; wait for saga to
reach `COMPENSATION_COMPLETE`; verify order status matches. Also verify that
`TradeVoided` event exists on `compensation-results` Kafka topic (using
`KafkaTestUtils`).

**Status:** [ ] todo

---

## Notes for Implementation Agent

- **ADR-001 constraint**: All state transitions (`onSettlementFailed`) must complete
  DB writes before calling `publisher.publishCompensationRequested(...)`.
  Use `@Transactional` on `onSettlementFailed` and `onTradeVoided`.

- **`entity.tradeId` null guard**: In `onSettlementFailed`, `entity.tradeId` should
  always be set (populated by `onTradeExecuted`). If null (unexpected), log ERROR
  and return without publishing — do not proceed with compensation.

- **Key pattern**: `compensation-results` topic carries a single event type
  (`TradeVoided`), unlike multi-type topics (risk-results, settlements). The
  `@KafkaListener` for `compensation-results` can use direct type deserialization
  (`fun onTradeVoided(event: TradeVoided)`) rather than `ConsumerRecord<String, Any>`.

- **`OrderEventListener.onCompensationResult`**: Use `event.orderId` from `TradeVoided`
  directly to find the order (no DB lookup by tradeId needed).

- **Existing test breakage**: No existing tests should break. `SettlementFailed`
  handling in `OrderEventListener` changes from `EXECUTION_FAILED` to
  `COMPENSATION_IN_PROGRESS`. If any existing tests assert `EXECUTION_FAILED` on
  settlement failure, update them.
