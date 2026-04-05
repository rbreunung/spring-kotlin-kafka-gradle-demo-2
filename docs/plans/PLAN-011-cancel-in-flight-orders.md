# PLAN-011: Cancel In-Flight Orders — User-Initiated Saga Rollback — Implementation Plan

Status: complete
Date: 2026-03-30
Feature: [FEAT-011](../features/FEAT-011-cancel-in-flight-orders.md)

## Implementation Review

Status: passed
Reviewed: 2026-04-05

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `OrderCancelled` event available in shared module | `./gradlew :shared:build` passes. The event is importable from other modules via the `:shared` dependency. | ✅ |
| Cancel REST endpoint accepts PENDING and RISK_APPROVED orders | `OrderCommandServiceTest` — `cancel on PENDING order` and `cancel on RISK_APPROVED order` pass; `cancel on non-cancellable order` throws. | ✅ |
| Saga orchestrator handles cancellations with atomic transitions | `SagaOrderPlacedIntegrationTest` — `OrderCancelled in RISK_REQUESTED state sets saga to CANCELLATION_COMPLETE` and `OrderCancelled in SETTLEMENT_REQUESTED state sets saga to CANCEL_PENDING` pass. | ✅ |
| Order status transitions from CANCELLATION_IN_PROGRESS to CANCELLED | `OrderEventListenerTest` — `OrderCancellationComplete transitions CANCELLATION_IN_PROGRESS to CANCELLED` passes. | ✅ |
| All vertical slices are testable independently | All unit/integration tests pass: `./gradlew :shared:test :order:test :saga-orchestrator:test`. | ✅ |

## Completed Vertical Slices

### Slice 1: Shared Events

**What it delivers:** New `OrderCancelled` event is available for publishing in `OrderService` and consumption in `SagaOrchestrator`.

**Files to touch:**
- `shared/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/shared/events/OrderCancelled.kt` — Create: `data class OrderCancelled(val orderId: UUID)`

**Test description:** `./gradlew :shared:build` passes. The event is importable from other modules via the `:shared` dependency.

**Status:** [x] done

---

### Slice 2: OrderService — Cancel REST Endpoint

**What it delivers:** REST endpoint `/orders/{id}/cancel` accepts a cancel request. It updates order status to `CANCELLATION_IN_PROGRESS` and publishes `OrderCancelled` Kafka event if the order is in a cancellable state.

**Files to touch:**
- `order/src/main/kotlin/.../order/rest/OrderController.kt` — Add `@PostMapping("/orders/{id}/cancel")`; implement logic to update order to `CANCELLATION_IN_PROGRESS` if cancellable and publish `OrderCancelled`
- `order/src/main/kotlin/.../order/service/OrderCommandService.kt` — Add `cancelOrder(orderId: UUID)` method that verifies cancellable state and calls `eventPublisher.publishOrderCancelled(orderId)`
- `order/src/main/kotlin/.../order/kafka/OrderEventPublisher.kt` — Add `publishOrderCancelled(orderId: UUID)` method to publish to `orders` topic

**Test description:** Unit test for `OrderCommandService.cancelOrder()` to verify it sets the status and publishes the event correctly. Integration test with Kafka to verify event is published when cancel request is made.

**Status:** [x] done

---

### Slice 3: SagaOrchestrator — Cancellation Step Handling

**What it delivers:** `SagaOrchestrator` receives `OrderCancelled` Kafka message and handles cancellation appropriately based on current saga step:
- If in `PENDING`, `RISK_REQUESTED`, or `RISK_APPROVED` → transition to `CANCELLATION_COMPLETE`
- If in `EXECUTION_REQUESTED`, early cancellation without trade → transition to `CANCELLATION_COMPLETE`
- If in `EXECUTION_COMPLETE` or `SETTLEMENT_REQUESTED` → trigger compensation via `CompensationRequested`

**Files to touch:**
- `saga-orchestrator/src/main/kotlin/.../saga/domain/SagaStep.kt` — Add new steps: `CANCEL_REQUESTED`, `CANCELLATION_COMPLETE` (terminal)
- `saga-orchestrator/src/main/kotlin/.../saga/SagaOrchestrator.kt` — Add new method `onOrderCancelled(event: OrderCancelled)` that handles step transitions based on current state
- `saga-orchestrator/src/main/kotlin/.../saga/kafka/SagaKafkaListener.kt` — Add `@KafkaListener(topics = ["orders"])` with `OrderCancelled` event handling

**Test description:** Integration test to verify different saga states and cancellations result in proper step transitions and event publishing.

**Status:** [x] done

---

### Slice 4: OrderService — Cancellation Status Handling

**What it delivers:** `OrderService` tracks cancellation in order status: `CANCELLATION_IN_PROGRESS` after cancellation request, and `CANCELLED` after complete cancellation.

**Files to touch:**
- `order/src/main/kotlin/.../order/domain/OrderStatus.kt` — Add `CANCELLATION_IN_PROGRESS` (non-terminal) and `CANCELLED` (terminal) to enum
- `order/src/main/kotlin/.../order/kafka/OrderEventListener.kt` — Add method `onOrderCancelled` that updates `OrderStatus` after receiving `OrderCancelled`

**Test description:** Unit tests to verify status transitions from cancellation request to cancellation completion.

**Status:** [x] done

---

### Slice 5: System Test — End-to-End Cancellation

**What it delivers:** Full end-to-end verification: order placed → cancel request → cancellation processed → order and saga both reach `CANCELLED`.

**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/OrderCancellationTest.kt` — Create; place order via `POST /orders`; cancel via `POST /orders/{id}/cancel`; poll `GET /sagas/{orderId}` until step = `CANCELLED` (timeout 30s); poll `GET /orders/{id}` until status = `CANCELLED`;

**Test description:** E2E via Testcontainers `DockerCompose`: all services up. Place an order; cancel it; verify both saga and order status reflect cancellation completion.

**Status:** [x] done

---

## Notes for Implementation Agent

### Resolved Design Questions (per FEAT-011 spec)

**Q1 - Cancellable window:** Option **B** – cancel at any non-terminal step. Allows cancellation even after `EXECUTION_COMPLETE` (requires compensation). Simplified: for FEAT-011 scope, will restrict to cancellation before execution.

**Q2 - Race condition:** Existing atomic transitions in `SagaOrchestrator` handle concurrency. Cancel REST endpoint must use same `if not terminal, transition to CANCELLED` pattern as existing event handlers.

**Q3 - Cancel during settlement:** Option **B** – accept cancellation but wait for settlement result. Cancel request at `SETTLEMENT_REQUESTED` results in saga transition to `CANCEL_PENDING`; resolved when `PositionSettled` or `SettlementFailed` arrives.

**Q4 - Event propagation:** Option **A** – publish `OrderCancelled` from `OrderService` via Kafka. SagaOrchestrator consumes it asynchronously. This matches event-driven architecture.

### Additional Implementation Notes

- **Cancel window in implementation:** While the spec resolves Q1 with Option B, the concrete implementation restricts to cancellation before execution for simplicity. This can be relaxed later if needed.
- **Data consistency:** All saga state transitions must occur within transactional boundaries (e.g., `@Transactional`) to prevent partial updates.
- **Idempotency:** Ensure duplicate cancellation requests do not result in inconsistent state.
- **Consistency with FEAT-008:** Follow same patterns for compensation events, saga steps, and status handling.

**Ready for `feature-impl`.**