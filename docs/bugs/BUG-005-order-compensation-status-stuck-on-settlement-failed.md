# BUG-005: Order status stuck — SettlementFailed arrives before TradeExecuted sets tradeId

Status: resolved
Severity: high
Date: 2026-03-29
Reporter: observed via CI failure on PR #15

---

## Environment

- OS: Ubuntu 24.04 (CI runner)
- Runtime/Version: JVM 21
- Framework/App Version: Spring Boot 3, Kafka
- Relevant Config: `SETTLEMENT_ALWAYS_FAIL_TRADER_IDS: "trader-comp-001"` (no artificial delay)

## Steps to Reproduce

1. Run `./gradlew :system-test:test` with `docker-compose.full.yml` using `SETTLEMENT_ALWAYS_FAIL_TRADER_IDS: "trader-comp-001"` and no `SETTLEMENT_ARTIFICIAL_DELAY_MS`
2. `SagaCompensationTest` places an order for `trader-comp-001`
3. Observe saga reaches `COMPENSATION_COMPLETE` (first await passes)
4. Observe order status never reaches `COMPENSATION_COMPLETE` (second await times out)

Alternatively in production: any scenario where settlement fails quickly (e.g. under load, broker backpressure on `executions` topic) can trigger the same gap.

## Expected Behavior

After saga reaches `COMPENSATION_COMPLETE`, the order status should also transition to `COMPENSATION_COMPLETE`.

## Actual Behavior

Order status remains stuck (never leaves `EXECUTED` or intermediate state).

```
org.awaitility.core.ConditionTimeoutException at SagaCompensationTest.kt:50
  — await for getOrder(orderId)?.status == "COMPENSATION_COMPLETE" timed out after 60s
```

Order service log (expected but not emitted):
```
applyTransition: order <id> has status EXECUTED but expected COMPENSATION_IN_PROGRESS, skipping transition to COMPENSATION_COMPLETE
```

## Affected Component(s)

- `order/src/main/kotlin/.../order/kafka/OrderEventListener.kt` — `onSettlement(SettlementFailed)` uses `findByTradeId` which can miss the order
- Indirectly: `order/src/main/kotlin/.../order/service/OrderCommandService.kt` — `applyTransition` silently skips on wrong `fromStatus`

## Severity

**high** — In production, any fast settlement failure (e.g. under broker backpressure or high load) can leave an order permanently stuck in `EXECUTED` instead of completing the compensation flow.

## Workaround

None in production. In CI, previously masked by `SETTLEMENT_ARTIFICIAL_DELAY_MS: "3000"` which gave the order service enough time to process `TradeExecuted` before `SettlementFailed` arrived.

---

## Root Cause

The order service's compensation path has an implicit ordering dependency between two independent Kafka consumer groups on different topics:

1. `executions` topic → `order-service` consumer group processes `TradeExecuted` → sets `tradeId` on the order entity
2. `settlements` topic → `order-service` consumer group processes `SettlementFailed` → calls `findByTradeId(event.tradeId)`

Step 2 requires step 1 to have completed first. But there is no guaranteed ordering between these two — they are different topics, different consumer groups. If `SettlementFailed` is consumed before `TradeExecuted`:

- `findByTradeId(event.tradeId)` returns `null` (tradeId not yet set on the order entity)
- `onSettlement` logs a warning and returns — order never reaches `COMPENSATION_IN_PROGRESS`
- When `TradeVoided` later arrives, `applyTransition(fromStatus = COMPENSATION_IN_PROGRESS)` also fails silently
- Order is permanently stuck

The race window is determined by how quickly settlement fails. With Resilience4j retry (3 attempts, 100ms wait, exponential multiplier), `SettlementFailed` is published in ~300ms. On a loaded CI runner, the order service may not have processed `TradeExecuted` in that window.

`SETTLEMENT_ARTIFICIAL_DELAY_MS: "3000"` accidentally masked this by adding 3s before settlement even began, effectively guaranteeing the order service had processed `TradeExecuted` first. Removing it (as part of BUG-004 fix) exposed the gap.

## Fix Summary

Added `orderId: UUID` to `SettlementFailed` in the shared events module. The settlement service's fallback methods (`settleFallback`, `settleBulkheadFallback`) now pass `order.id` when publishing the event. The order service's `onSettlement(SettlementFailed)` handler was simplified to use `event.orderId` directly instead of `findByTradeId(event.tradeId)`, removing the dependency on `TradeExecuted` being processed first.

- **Files changed:**
  - `shared/.../events/SettlementFailed.kt` — added `orderId: UUID` field
  - `settlement/.../kafka/SettlementEventPublisher.kt` — added `orderId` parameter to `publishSettlementFailed`
  - `settlement/.../SettlementService.kt` — pass `order.id` in both fallback methods
  - `order/.../kafka/OrderEventListener.kt` — use `event.orderId` directly, removed `findByTradeId` lookup
  - `settlement/.../BulkheadFallbackTest.kt` — updated mock verify call
  - `order/.../kafka/OrderEventListenerTest.kt` — added two reproduction tests covering the race scenario
  - `system-test/.../SettlementFailureTest.kt` — updated `SettlementFailed` construction to include `orderId`
- **Test added:** `order/src/test/kotlin/.../order/kafka/OrderEventListenerTest.kt` — `SettlementFailed reaches COMPENSATION_IN_PROGRESS even when tradeId not yet set on order` and `full compensation chain completes when SettlementFailed arrives before TradeExecuted`
- **Commit:** `df4259f`
