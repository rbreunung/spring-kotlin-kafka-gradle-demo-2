# BUG-003: Settlement service fails all orders after the first — original exception not logged

Status: resolved
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

## Root Cause

`PositionEntity` is a Kotlin `data class` annotated with `@Entity`, but the `settlement` module was missing the `kotlin("plugin.jpa")` compiler plugin. Without this plugin, Kotlin does not generate the no-arg constructor that Hibernate requires to hydrate entities from a SQL `SELECT` result set.

**Why order 1 succeeded:** `SettlementService.updatePosition()` takes the `createPosition()` path for a new position, which calls `positionRepository.save(newEntity)` → `em.persist()`. Hibernate persists the entity constructed in code — no SELECT is needed, so no no-arg constructor is needed.

**Why orders 2+ failed:** The `applyTrade()` path calls `positionRepository.findByTraderIdAndSymbol()`, which executes a `SELECT`. Hibernate attempts to instantiate `PositionEntity` from the result set, requires a no-arg constructor, finds none, and throws:

```
org.hibernate.InstantiationException: No default constructor for entity 'de.antrophos.demo.spring.kafka.trader.settlement.domain.PositionEntity'
```

This exception propagated through Resilience4j (3 retry attempts, all failing the same way), `settleFallback` published `SettlementFailed`, and the saga compensated. The exception was invisible in logs because `SettlementKafkaListener` had no exception handling.

**Why `PositionPersistenceTest` passed despite the bug:** The test uses `@DataJpaTest`, which wraps each test method in a single transaction. When `findByTraderIdAndSymbol()` is called within that transaction, Hibernate returns the entity from the first-level cache (no SELECT needed), avoiding the instantiation problem.

## Fix Summary

Added `kotlin("plugin.jpa")` to `settlement/build.gradle.kts`. This plugin generates a no-arg constructor for all `@Entity`-annotated classes, allowing Hibernate to hydrate entities from the database.

The fix mirrors the existing pattern in `saga-orchestrator`, which has `kotlin("plugin.jpa")` and uses `SagaStateEntity` as a `data class`.

Also added structured error logging (try-catch) in `SettlementKafkaListener.onSettlementRequested()` so that any future settlement exception is logged with `tradeId` before propagating.

- **Test added:** `settlement/src/test/kotlin/.../settlement/SequentialOrdersIntegrationTest.kt` — `second SettlementRequested for same traderId and symbol also produces PositionSettled`
- **Commit:** `523cff1` — `fix(BUG-003): add kotlin plugin.jpa to settlement — fixes no-arg constructor for PositionEntity`
