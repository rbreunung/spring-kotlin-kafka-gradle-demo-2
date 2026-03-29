# BUG-004: SagaCompensationTest flaky — timing race between test injection and real settlement

Status: resolved
Severity: medium
Date: 2026-03-29
Reporter: rbreunung

---

## Environment

- OS: GitHub Actions (Ubuntu runner)
- Runtime/Version: JVM 21
- Framework/App Version: Spring Boot 3.x, Spring Kafka 3.x, Testcontainers Docker Compose
- Relevant Config: `docker-compose.full.yml` system-test stack

## Steps to Reproduce

1. Run the system-test suite in CI: `./gradlew :system-test:test`
2. Observe `SagaCompensationTest` intermittently fails with `ConditionTimeoutException at SagaCompensationTest.kt:89`

## Expected Behavior

`SagaCompensationTest` reliably verifies the saga compensation flow: after a settlement failure, the saga and the order both reach `COMPENSATION_COMPLETE`.

## Actual Behavior

The test times out waiting for `order.status == "COMPENSATION_COMPLETE"` (30-second window). The saga has already reached `COMPENSATION_COMPLETE`, but the order status is stuck.

```
SagaCompensationTest > saga compensation flow reaches COMPENSATION_COMPLETE after SettlementFailed() FAILED
    org.awaitility.core.ConditionTimeoutException at SagaCompensationTest.kt:89
```

## Affected Component(s)

`system-test` — `SagaCompensationTest`; `settlement-service` — `docker-compose.full.yml` configuration

## Severity

**medium** — CI is unreliable; the compensation flow works correctly in production but the test cannot verify it consistently.

## Workaround

A 3-second artificial delay (`SETTLEMENT_ARTIFICIAL_DELAY_MS=3000`) was previously added to `docker-compose.full.yml` to widen the injection window. This reduces but does not eliminate the race.

---

## Root Cause

`SagaCompensationTest` tests saga compensation by:
1. Placing an order
2. Polling until the saga reaches `SETTLEMENT_REQUESTED`
3. Manually injecting a `SettlementFailed` event via `KafkaTemplate`
4. Waiting for the saga then the order to reach `COMPENSATION_COMPLETE`

The race: the real settlement service also processes the `SettlementRequested` message concurrently. If it publishes `PositionSettled` before the test's injected `SettlementFailed` is processed, the order service transitions `EXECUTED → SETTLED` (terminal). `SETTLED` is a terminal `OrderStatus`, blocking all further transitions — so the order can never reach `COMPENSATION_COMPLETE`. The 30-second await at line 89 times out.

The 3-second artificial delay was meant to keep the settlement window open, but it is not a reliable fix: on slow CI runners, 3 seconds is not always enough.

The fundamental problem is that the test races the real settlement service. Settlement must be made to fail **deterministically** for the test order so there is no race to win.

## Fix Summary

Added `settlement.always-fail-trader-ids` config property to `SettlementService`. When an order's `traderId` is in the configured set, `simulateFailure()` throws `SettlementException` unconditionally — before any position update. The property is empty by default and is set to `trader-comp-001` in `docker-compose.full.yml`.

`SagaCompensationTest` was simplified to place the order for `trader-comp-001` and wait directly for `COMPENSATION_COMPLETE` on both saga and order, with 90s and 60s timeouts respectively. The `kafkaTemplate` injection logic and the early-skip for `SETTLED` were removed. The test no longer races the settlement service.

The `SETTLEMENT_ARTIFICIAL_DELAY_MS: "3000"` workaround was removed from `docker-compose.full.yml`.

- **Files changed:**
  - `settlement/src/main/kotlin/.../settlement/SettlementService.kt` — added `alwaysFailTraderIds` property and check in `simulateFailure()`
  - `settlement/src/main/resources/application.yml` — added `settlement.always-fail-trader-ids:` (empty default)
  - `docker-compose.full.yml` — replaced `SETTLEMENT_ARTIFICIAL_DELAY_MS` with `SETTLEMENT_ALWAYS_FAIL_TRADER_IDS: "trader-comp-001"`
  - `system-test/src/test/kotlin/.../SagaCompensationTest.kt` — removed injection logic, simplified to direct await
- **Commit:** `868081e`
