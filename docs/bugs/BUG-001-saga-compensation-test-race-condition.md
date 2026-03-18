# BUG-001: SagaCompensationTest race condition and URL deprecation warning

Status: resolved
Date: 2026-03-18
Severity: high
Affected branch: feat/FEAT-009-observability
Related: FEAT-008, FEAT-009, RETRO-014

---

## Intake

**Description:**
Two CI failures blocking the `feat/FEAT-009-observability` branch from merging:
1. `SagaCompensationTest` consistently fails in GitHub Actions with `ConditionTimeoutException` at `SagaCompensationTest.kt:79` — the test times out waiting for the saga to reach `COMPENSATION_COMPLETE` after injecting a `SettlementFailed` event.
2. Kotlin compiler emits a deprecation warning: `'constructor URL(String!)' is deprecated. Deprecated in Java` at `SystemTestBase.kt:132`.

**Environment:**
- CI: GitHub Actions (PR verification workflow)
- Runtime: Docker Compose, Java 21, Kafka 3.9 (KRaft)
- OS: Linux (ubuntu-latest runner)

**Reproduction steps:**
1. Push a branch with no changes to `system-test` — the test fails in CI
2. Locally (fast machine): `docker compose -f docker-compose.full.yml up --build -d && ./gradlew :system-test:test`

**Expected behavior:**
`SagaCompensationTest` passes; no deprecation warnings in `system-test:compileTestKotlin`.

**Actual behavior:**
```
SagaCompensationTest > saga compensation flow reaches COMPENSATION_COMPLETE after SettlementFailed() FAILED
    org.awaitility.core.ConditionTimeoutException at SagaCompensationTest.kt:79

w: file:///...SystemTestBase.kt:132:24 'constructor URL(String!)' is deprecated. Deprecated in Java
```

**Affected components:**
- `system-test` module — `SagaCompensationTest`, `SystemTestBase`
- `saga-orchestrator` — `SagaOrchestrator.onPositionSettled` (missing step guard)
- `docker-compose.full.yml` — settlement service has no artificial delay

**Known workaround before fix:** None.

---

## Root Cause Analysis

### Bug 1 — Race condition (primary)

`SagaCompensationTest` injects a `SettlementFailed` event manually into the `settlements` Kafka topic to simulate a settlement failure. Both `SettlementFailed` (from the test) and `PositionSettled` (always produced by the settlement service, which has `simulate-failure-probability: 0.0`) use `tradeId` as the Kafka key, landing in the same partition.

The test polls the saga REST endpoint every **1 second**. The settlement service processes `SettlementRequested` within ~50ms (Kafka delivery in Docker network + H2 write) and publishes `PositionSettled` immediately. By the time the test's 1-second poll detects `SETTLEMENT_REQUESTED`, `PositionSettled` is already in the `settlements` partition at a lower offset.

The saga-orchestrator processes `PositionSettled` first → transitions to `SETTLED` (terminal) → rejects the test's `SettlementFailed` (terminal guard) → test waits for `COMPENSATION_COMPLETE` → `ConditionTimeoutException`.

### Bug 2 — Missing step guard in `onPositionSettled` (secondary)

`SagaOrchestrator.onPositionSettled` had no guard checking that the saga was in `SETTLEMENT_REQUESTED` state before transitioning to `SETTLED`. If compensation had already started (saga at `COMPENSATION_REQUESTED`), a late `PositionSettled` would silently override the compensation flow to `SETTLED`. This made Failure Mode 2 possible (SettlementFailed processed first, then PositionSettled overrides → compensation aborted).

### Bug 3 — URL deprecation

`java.net.URL(String)` constructor is deprecated in Java 20+. `SystemTestBase.isHttpReady` used it at line 132.

---

## Fix Summary

Four changes made on `feat/FEAT-009-observability` (no separate fix branch — justified because the bugs were blocking this specific PR and all changes belong on the same branch):

1. **`saga-orchestrator/.../SagaOrchestrator.kt`** — added step guard to `onPositionSettled`: only processes event when saga step == `SETTLEMENT_REQUESTED`. Matches the existing guard pattern in `onSettlementFailed` and `onTradeVoided`.

2. **`docker-compose.full.yml`** — added `SETTLEMENT_ARTIFICIAL_DELAY_MS: "3000"` to the settlement service environment. Delays `PositionSettled` publication by 3 seconds, giving the test a reliable ~1.5-second injection window (worst case: 1000ms poll + ~500ms REST overhead = ~1500ms, comfortably within 3000ms).

3. **`system-test/.../SystemTestBase.kt`** — replaced `URL(url).openConnection()` with `URI.create(url).toURL().openConnection()`. Updated import from `java.net.URL` to `java.net.URI`.

4. **`saga-orchestrator/.../SagaOrchestratorCompensationTest.kt`** — added unit test `late PositionSettled on COMPENSATION_REQUESTED is skipped` to cover the new step guard. Also added `PositionSettled on terminal saga is silently skipped` and `PositionSettled for unknown tradeId is silently skipped`.

## Files Changed

| File | Change |
|------|--------|
| `saga-orchestrator/src/main/kotlin/.../SagaOrchestrator.kt` | Step guard on `onPositionSettled` |
| `docker-compose.full.yml` | `SETTLEMENT_ARTIFICIAL_DELAY_MS: "3000"` on settlement service |
| `system-test/src/test/kotlin/.../SystemTestBase.kt` | `URI.create(url).toURL()` replaces deprecated `URL(url)` |
| `saga-orchestrator/src/test/kotlin/.../SagaOrchestratorCompensationTest.kt` | 3 new unit tests for `onPositionSettled` edge cases |

## Notes for Future Work

- When FEAT-009 adds Zipkin to `docker-compose.full.yml`, the `SETTLEMENT_ARTIFICIAL_DELAY_MS` env var and its comment must be preserved.
- Pre-existing test gaps remain out of scope: `SettlementFailed` and `TradeVoided` for unknown tradeId are not covered by unit tests. These are low-risk (trivial null guard) but could be addressed in a future quality pass.
