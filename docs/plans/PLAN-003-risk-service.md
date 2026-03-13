# PLAN-003: Risk Service — Implementation Plan

Status: draft
Date: 2026-03-13
Feature: [FEAT-003](../features/FEAT-003-risk-service.md)

## Progress

> Agent: update after each completed slice. Remove entire section when all slices done.

Current Slice: 1
Completed Slices: []
Last Updated: 2026-03-13

## Implementation Review

> Agent: fill this section during the final review step of feature-impl.

Status: pending
Reviewed: —

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `RiskCheckRequested` compiles | — | pending |
| `RiskApproved` published for quantity ≤ 10_000 | — | pending |
| `RiskRejected("quantity-exceeds-limit")` for quantity > 10_000 | — | pending |
| `RiskRejected("evaluation-failed")` when external call throws | — | pending |
| CB opens after 50% failure rate; fallback `RiskRejected("risk-service-unavailable")` fires | — | pending |
| Poison message logged and skipped | — | pending |

Gaps: —

---

## Open Questions

- [x] Sliding window type → COUNT_BASED (10 calls, 50%, 10s wait)
- [x] CB fallback → `RiskRejected("risk-service-unavailable")`
- [x] External call failure mode → configurable probability (`risk.simulate-failure-probability`)
- [x] Idempotency → out of scope

---

## Vertical Slices

### Slice 1: Event Contract

**What it delivers:** `RiskCheckRequested` is available to all modules; `:shared` compiles cleanly.

**Files to touch:**
- `shared/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/shared/events/RiskCheckRequested.kt` — Create: `data class RiskCheckRequested(val order: Order)`

**Test description:** `./gradlew :shared:build` passes. Optionally: a data-class equality test similar to other events in `:shared`.

**Status:** [ ] todo

---

### Slice 2: Kafka Consumer

**What it delivers:** `RiskKafkaListener` consumes `RiskCheckRequested` from the `risk-checks` topic and delegates to `RiskService`. The listener is wired, configured, and integration-tested.

**Files to touch:**
- `risk/src/main/kotlin/.../risk/kafka/RiskKafkaListener.kt` — Create: `@KafkaListener(topics = ["risk-checks"])` consuming `RiskCheckRequested`; delegates to `RiskService.handle(request)`
- `risk/src/main/kotlin/.../risk/RiskService.kt` — Modify: add `handle(request: RiskCheckRequested)` entry point
- `risk/src/main/resources/application.yml` — Modify: add Kafka consumer config (group-id `risk-service`, `JsonDeserializer` + `ErrorHandlingDeserializer`, trusted packages, type headers)
- `risk/src/test/resources/application.yml` — Create: sentinel `bootstrap-servers: localhost:9999`, `listener.auto-startup: false`

**Test description:** `@EmbeddedKafka` integration test — publish one `RiskCheckRequested` to `risk-checks`; verify `RiskService.handle()` is called (use a spy or observable side-effect). Poison message test: publish malformed JSON; verify no exception propagates.

**Status:** [ ] todo

---

### Slice 3: Kafka Producer

**What it delivers:** `RiskEventPublisher` publishes `RiskApproved` or `RiskRejected` to `risk-results`. The producer is wired into `RiskService`.

**Files to touch:**
- `risk/src/main/kotlin/.../risk/kafka/RiskEventPublisher.kt` — Create: `KafkaTemplate<String, Any>` wrapper with `publishApproved(orderId)` and `publishRejected(orderId, reason)` methods; key = `orderId.toString()`, topic = `risk-results`
- `risk/src/main/kotlin/.../risk/RiskService.kt` — Modify: inject `RiskEventPublisher`; publish after evaluation
- `risk/src/main/resources/application.yml` — Modify: add Kafka producer config (`JsonSerializer`)

**Test description:** `@EmbeddedKafka` integration test — publish `RiskCheckRequested`; consume from `risk-results` and assert `RiskApproved` arrives with correct `orderId`.

**Status:** [ ] todo

---

### Slice 4: Business Rule

**What it delivers:** Quantity threshold logic is in `RiskService` — orders with `quantity ≤ 10_000` proceed to evaluation; above that, `RiskRejected("quantity-exceeds-limit")` is published immediately without calling the external client.

**Files to touch:**
- `risk/src/main/kotlin/.../risk/RiskService.kt` — Modify: add quantity check before delegating to `RiskExternalClient`
- `risk/src/main/kotlin/.../risk/external/RiskExternalClient.kt` — Create: `evaluate(order: Order)` stub; reads `risk.simulate-failure-probability`; throws `RiskEngineException` when probability triggers (using `ThreadLocalRandom`); otherwise returns normally
- `risk/src/main/resources/application.yml` — Modify: add `risk.simulate-failure-probability: 0.0`
- `risk/src/test/resources/application.yml` — Modify: add `risk.simulate-failure-probability: 0.0`

**Test description:** Unit tests for `RiskService`:
- `quantity = 100` → `RiskApproved` published
- `quantity = 10_001` → `RiskRejected("quantity-exceeds-limit")` published; `RiskExternalClient` not called
- `quantity = 10_000` (boundary) → `RiskApproved` published

**Status:** [ ] todo

---

### Slice 5: Circuit Breaker

**What it delivers:** `RiskExternalClient.evaluate()` is protected by a Resilience4j Circuit Breaker. When the CB is OPEN, a fallback publishes `RiskRejected("risk-service-unavailable")` without calling the evaluator. The CB is observable and testable by setting `risk.simulate-failure-probability=1.0`.

**Files to touch:**
- `risk/src/main/kotlin/.../risk/external/RiskExternalClient.kt` — Modify: add `@CircuitBreaker(name = "riskEngine", fallbackMethod = "evaluateFallback")` to `evaluate()`; add `evaluateFallback(order, ex)` method that calls publisher with rejected reason
- `risk/src/main/kotlin/.../risk/RiskService.kt` — Modify: inject `CircuitBreakerRegistry` or rely on annotation-driven CB; pass `orderId` context to fallback via `RiskExternalClient`
- `risk/src/main/resources/application.yml` — Modify: add `resilience4j.circuitbreaker.instances.riskEngine` config block

**Test description:** `@EmbeddedKafka` + `@SpringBootTest` integration test:
1. Set `risk.simulate-failure-probability=1.0` via `@TestPropertySource`
2. Publish 6 `RiskCheckRequested` events (filling the CB window past 50% threshold)
3. Assert the first 5–6 results are `RiskRejected("evaluation-failed")`
4. Assert subsequent events (after window fills) produce `RiskRejected("risk-service-unavailable")` — CB is now OPEN
5. Assert `RiskExternalClient.evaluate()` is not called during OPEN state

**Status:** [ ] todo
