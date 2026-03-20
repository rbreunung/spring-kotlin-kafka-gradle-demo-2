# BUG-003: Settlement service fails all orders after the first — original exception not logged

Status: open
Severity: high
Date: 2026-03-20
Reporter: rbreunung

---

## Environment

- OS: Windows 11 / Docker Desktop
- Runtime/Version: JVM 21
- Framework/App Version: Spring Boot 3.x, Spring Kafka 3.3.13, Kafka 3.9.1, Resilience4j, H2 in-memory DB
- Relevant Config: Full Docker Compose stack (`docker-compose.full.yml`), fresh stack after `docker system prune`

## Steps to Reproduce

1. Start full stack: `docker compose -f docker-compose.full.yml up -d`
2. Wait for all services ready
3. Place first order: `POST http://localhost:8080/orders` with `{"traderId":"T001","symbol":"AAPL","quantity":100,"side":"BUY"}`
4. Wait for saga to reach `SETTLED` (`GET http://localhost:8085/sagas/{orderId}`) ✓
5. Place second order (same or different traderId/symbol)
6. Poll saga state for second order

## Expected Behavior

Second and subsequent orders complete through to `SETTLED` and trigger notifications.

## Actual Behavior

Every order after the first reaches `COMPENSATION_COMPLETE`. The settlement service fails processing, Resilience4j exhausts 3 retry attempts, `settleFallback` publishes `SettlementFailed`, and the saga compensates.

The original exception thrown by `SettlementService.settle()` is **never logged** — no structured error logging exists in `SettlementKafkaListener` or the error handler. Only the downstream DLQ activity is visible.

## Observed Log Evidence

```
# Order 1 — SUCCEEDS
11:08:57.123  INFO  Received SettlementRequested for tradeId=336521e1
11:09:00.270  INFO  [settlement-service-producer-1] ProducerId set to 4 with epoch 0
→ saga SETTLED ✓

# Order 2 — FAILS
11:09:06.500  INFO  Received SettlementRequested for tradeId=c74a4e75
11:09:15.723  WARN  Destination resolver returned non-existent partition dlq.settlements-0
11:09:15.726  INFO  [producer-1] ProducerConfig values: value.serializer=JsonSerializer  ← DLQ producer initialised
11:09:15.732  WARN  dlq.settlements=UNKNOWN_TOPIC_OR_PARTITION  ← topic auto-created
→ saga COMPENSATION_COMPLETE ✗

# Orders 3 and 4 — same pattern, faster (~3s) because DLQ already initialised
11:11:18.011  INFO  Received SettlementRequested for tradeId=6cf6fedc → COMPENSATION_COMPLETE ✗
11:11:43.515  INFO  Received SettlementRequested for tradeId=195a399e → COMPENSATION_COMPLETE ✗
```

Key timing:
- Order 2 fails in ~9 seconds: ~300ms Resilience4j retries + ~8s DLQ producer init + `dlq.settlements` topic auto-creation
- Orders 3+ fail in ~3 seconds: DLQ already initialised from order 2

## Affected Component(s)

`settlement-service` — `SettlementService.settle()`, `SettlementKafkaListener`

## Severity

**high** — only the first order per stack run succeeds. All subsequent orders trigger saga compensation. Feature is functionally broken for repeat trading.

## Workaround

None within a stack run. Only the first order per fresh stack startup succeeds.

---

## Hypotheses (unconfirmed — original exception not yet captured)

The following were considered and not yet ruled out:

1. **JPA merge issue with Kotlin `data class copy()`**: `PositionEntity` uses Kotlin `data class`. `applyTrade` calls `existing.copy(quantity = newQty, ...)` which creates a new detached entity instance. Spring Data `save()` calls `merge()` for non-null ID. This should UPDATE, but JPA behaviour with detached `data class` copies may differ from expectations.

2. **`@Retry` + non-transactional `updatePosition`**: `settle()` is not `@Transactional`. If attempt 1 succeeds in saving to DB but fails at `publishPositionSettled`, Resilience4j retries the entire `settle()`. On retry, `updatePosition` runs again, potentially double-counting the position quantity. If a constraint is violated on retry, all subsequent orders to the same trader/symbol would fail.

3. **Resilience4j state corruption**: Unlikely with `@Retry` (stateless per call), but possible if `@Bulkhead` max-concurrent-calls is exhausted due to a stuck thread from order 1.

4. **H2 connection pool / locking**: H2 `mem:settlementdb` may exhibit locking behaviour after an uncommitted transaction from a previous processing attempt.

## Required Next Steps

1. **Implement structured error logging** in `SettlementKafkaListener` (or via a custom `CommonErrorHandler`) to capture and log the original exception with `tradeId` before it propagates. This is the critical missing piece.
   - See: `settlement/src/main/kotlin/.../settlement/kafka/SettlementKafkaListener.kt`
   - See: RETRO-017 Suggested Improvement #2

2. **Re-run and capture the actual exception** — once logging is in place, the root cause will be immediately visible.

3. **Write a module-level test** that processes two `SettlementRequested` events sequentially for the same `traderId`/`symbol` and asserts both produce `PositionSettled`.

## Root Cause

> To be filled after structured error logging is implemented and the actual exception is captured.

## Fix Summary

> To be filled after root cause is confirmed.

- **Test added:** —
- **Commit:** —
