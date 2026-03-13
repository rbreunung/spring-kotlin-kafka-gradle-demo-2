# FEAT-003: Risk Service — Kafka Integration and Resilience4j Circuit Breaker

Status: complete
Date: 2026-03-13
Author: Claude

---

## Context & Motivation

FEAT-002 implemented the OrderService as the saga entry point. All other services remain stubs. To observe any saga progression, the platform needs at least one downstream service capable of reacting to events and producing results.

The RiskService is the first downstream step in the saga. It already exists as a minimal stub (`RiskService.evaluate()` always returns `true`). This feature wires it into the Kafka pipeline: consuming a risk check request, applying a configurable quantity-based rule, and publishing an approval or rejection result. A Resilience4j Circuit Breaker wraps the simulated external risk engine call to demonstrate the resilience pattern.

After this feature, a `RiskCheckRequested` event can be manually published and a `RiskApproved` or `RiskRejected` response observed on the `risk-results` topic — the foundation for end-to-end saga testing in FEAT-004.

## Goals

- [x] Add `RiskCheckRequested(val order: Order)` event to `:shared`
- [x] Kafka consumer on `risk-checks` topic: consume `RiskCheckRequested`, trigger evaluation
- [x] Business rule: approve orders with `quantity ≤ 10_000`; reject above with reason `"quantity-exceeds-limit"`
- [x] Simulated external risk engine call (`RiskExternalClient`) with configurable failure probability (`risk.simulate-failure-probability`, default `0.0`)
- [x] Resilience4j Circuit Breaker (COUNT_BASED, window=10, threshold=50%, open=10s) wrapping `RiskExternalClient`
- [x] CB fallback when OPEN: publish `RiskRejected(orderId, "risk-service-unavailable")`
- [x] Kafka producer on `risk-results` topic: `RiskApproved(orderId)` or `RiskRejected(orderId, reason)`
- [x] Integration tests with embedded Kafka covering consumer, producer, business rule, and CB behaviour

## Non-Goals

- No idempotency or dedup of duplicate `RiskCheckRequested` events
- No real external risk engine HTTP calls (simulated only; no WireMock or HTTP client)
- No REST API (Kafka-only service)
- No persistence (stateless)
- No position-based risk limits (e.g. per-trader position checks)
- No authentication or authorisation

## Architecture

### Component Design

```
risk-checks topic
      │
      ▼
RiskKafkaListener
      │  (RiskCheckRequested)
      ▼
RiskService
  ├── business rule: quantity ≤ 10_000?
  │       ├── YES → call RiskExternalClient.evaluate(order)  ← Circuit Breaker wraps this
  │       └── NO  → reject immediately ("quantity-exceeds-limit")
  │
  ├── RiskExternalClient.evaluate() succeeds → RiskApproved
  ├── RiskExternalClient.evaluate() throws  → RiskRejected ("evaluation-failed")
  └── CB OPEN (fallback)                   → RiskRejected ("risk-service-unavailable")
      │
      ▼
RiskEventPublisher
      │
      ▼
risk-results topic
```

### Circuit Breaker State Machine

```
CLOSED ──(failure rate ≥ 50% over last 10 calls)──▶ OPEN ──(wait 10s)──▶ HALF-OPEN
  ▲                                                                             │
  └──────────(3 test calls succeed)────────────────────────────────────────────┘
                                                     │
                                         (any test call fails)──▶ OPEN again
```

- **CLOSED:** calls pass through to `RiskExternalClient.evaluate()`
- **OPEN:** fallback fires immediately; `RiskRejected("risk-service-unavailable")` published
- **HALF-OPEN:** 3 test calls permitted; all must succeed to return to CLOSED

### Kafka

**Consumer** (group-id `risk-service`):

| Topic | Event | Handler |
|---|---|---|
| `risk-checks` | `RiskCheckRequested` | `RiskKafkaListener.onRiskCheckRequested()` |

**Producer** (key = `orderId.toString()`):

| Topic | Event | Trigger |
|---|---|---|
| `risk-results` | `RiskApproved(orderId)` | Evaluation succeeds and quantity within limit |
| `risk-results` | `RiskRejected(orderId, reason)` | Quantity exceeds limit, evaluation throws, or CB is OPEN |

Deserialization uses the same pattern as `OrderService` (FEAT-002): `JsonDeserializer` with `__TypeId__` headers + `ErrorHandlingDeserializer`.

## Data Model

### New event in `:shared`

```kotlin
data class RiskCheckRequested(val order: Order)
```

Location: `shared/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/shared/events/RiskCheckRequested.kt`

## API Surface / Interface

| Interface | Type | Description |
|---|---|---|
| `risk-checks` topic | Kafka consumer | Receives `RiskCheckRequested` |
| `risk-results` topic | Kafka producer | Publishes `RiskApproved` or `RiskRejected` |
| `risk.simulate-failure-probability` | Spring property | `Double` in `[0.0, 1.0]`; probability that `RiskExternalClient.evaluate()` throws; default `0.0` |

## Edge Cases & Error Handling

| Scenario | Expected Behavior |
|---|---|
| `quantity > 10_000` | `RiskRejected(orderId, "quantity-exceeds-limit")` immediately, without calling `RiskExternalClient` |
| `RiskExternalClient.evaluate()` throws (failure probability triggered) | `RiskRejected(orderId, "evaluation-failed")`; failure counted by CB |
| CB transitions to OPEN | Fallback fires; `RiskRejected(orderId, "risk-service-unavailable")` without calling evaluator |
| CB in HALF-OPEN, test call fails | CB returns to OPEN; `RiskRejected` via fallback |
| Kafka deserialization fails (poison message) | `ErrorHandlingDeserializer` logs and skips — no result published |
| Duplicate `RiskCheckRequested` for same orderId | Two results published (no dedup — OrderService and Saga guard against duplicate transitions) |

## Configuration

### `risk/src/main/resources/application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: risk-service
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true

risk:
  simulate-failure-probability: 0.0

resilience4j:
  circuitbreaker:
    instances:
      riskEngine:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
```

### `risk/src/test/resources/application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9999
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: risk-service
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true
    listener:
      auto-startup: false

risk:
  simulate-failure-probability: 0.0
```

## Acceptance Criteria

- [x] `./gradlew :shared:build` — `RiskCheckRequested` compiles
- [x] `./gradlew :risk:test` — all tests pass
- [x] Consuming `RiskCheckRequested` with `quantity ≤ 10_000` and `simulate-failure-probability=0.0` → `RiskApproved` published to `risk-results`
- [x] Consuming `RiskCheckRequested` with `quantity > 10_000` → `RiskRejected("quantity-exceeds-limit")` published
- [x] With `simulate-failure-probability=1.0`, consuming one `RiskCheckRequested` → `RiskRejected("evaluation-failed")` published; failure counted by CB
- [x] With `simulate-failure-probability=1.0`, after 5 failures (50% of 10-call window, via `minimumNumberOfCalls`), CB opens → subsequent events receive `RiskRejected("risk-service-unavailable")` via fallback without calling evaluator
- [x] Kafka poison message (malformed JSON) → logged and skipped; no exception propagated

## Files to Create / Modify

| Path | Action |
|---|---|
| `shared/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/shared/events/RiskCheckRequested.kt` | Create |
| `risk/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/risk/kafka/RiskKafkaListener.kt` | Create |
| `risk/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/risk/kafka/RiskEventPublisher.kt` | Create |
| `risk/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/risk/external/RiskExternalClient.kt` | Create |
| `risk/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/risk/RiskService.kt` | Modify — inject publisher + external client; add CB fallback |
| `risk/src/main/resources/application.yml` | Modify — add Kafka config, CB config, `risk.simulate-failure-probability` |
| `risk/src/test/resources/application.yml` | Create — sentinel Kafka config for non-Kafka test slices |

## Implementation Notes

**CB implementation — programmatic vs annotation-based:**
`@CircuitBreaker` annotation AOP was not used because `FallbackExecutor` (required by `CircuitBreakerAspect`) is not auto-configured by `spring-cloud-starter-circuitbreaker-resilience4j`. Instead, `RiskExternalClient` uses `CircuitBreakerRegistry.circuitBreaker("riskEngine").executeSupplier { }` programmatically. `RiskService` catches `RiskEngineException` (evaluation failure, counted) and `CallNotPermittedException` (CB open, not counted) separately.

**`minimumNumberOfCalls: 5`:**
Added to the CB config (both main and test `application.yml`). This allows the CB test to open after exactly 5 failure calls rather than requiring the full `slidingWindowSize=10`. The test `RiskCircuitBreakerIntegrationTest` verifies calls 1–5 produce `"evaluation-failed"` and call 6 produces `"risk-service-unavailable"`.

**ObjectMapper in integration tests:**
`@SpringBootTest` with `spring-boot-starter` (no web) does not auto-configure a Jackson `ObjectMapper` bean. Integration tests instantiate `ObjectMapper().registerKotlinModule()` directly.

## Related Docs

- [FEAT-002: Order Service](FEAT-002-order-service.md)
- [FEAT-004: Saga Orchestrator](FEAT-004-saga-orchestrator.md)
- [Architecture](../arch/architecture.md)
- [PLAN-003: Implementation Plan](../plans/PLAN-003-risk-service.md)
