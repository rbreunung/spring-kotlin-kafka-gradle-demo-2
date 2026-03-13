# PLAN-005: Execution Service — Implementation Plan

Status: draft
Date: 2026-03-13
Feature: [FEAT-005](../features/FEAT-005-execution-service.md)

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
| `ExecutionRequested` consumed → `TradeExecuted` published with matching orderId | — | pending |
| `executedPrice` within ±2% of base price | — | pending |
| Poison message logged and skipped | — | pending |

Gaps: —

---

## Open Questions

- [x] Resilience4j → none (not in architecture spec for ExecutionService)
- [x] Persistence → none (stateless)
- [x] Price simulation → ±2% random, configurable base price (`execution.base-price: 100.00`)
- [x] Idempotency → out of scope

---

## Vertical Slices

### Slice 1: Kafka Consumer

**What it delivers:** `ExecutionKafkaListener` consumes `ExecutionRequested` from `execution-requests` and delegates to `ExecutionService`. The application context loads with Kafka consumer config.

**Files to touch:**
- `execution/src/main/kotlin/.../execution/kafka/ExecutionKafkaListener.kt` — Create: `@KafkaListener(topics = ["execution-requests"])` with `ExecutionRequested` parameter; delegates to `ExecutionService.execute(order)`
- `execution/src/main/kotlin/.../execution/ExecutionService.kt` — Modify: add `execute(order: Order)` entry point
- `execution/src/main/resources/application.yml` — Modify: add Kafka consumer config (group-id `execution-service`, `JsonDeserializer` + `ErrorHandlingDeserializer`, trusted packages, type headers)
- `execution/src/test/resources/application.yml` — Create: sentinel `bootstrap-servers: localhost:9999`, `listener.auto-startup: false`, same consumer config

**Test description:** `@EmbeddedKafka` integration test — publish one `ExecutionRequested` to `execution-requests`; verify `ExecutionService.execute()` is called (observable via spy or logged side-effect). Poison message test: publish malformed JSON; verify no exception propagates.

**Status:** [ ] todo

---

### Slice 2: Kafka Producer

**What it delivers:** `ExecutionEventPublisher` publishes `TradeExecuted` to `executions`. The publisher is injected into `ExecutionService` and called after generating a `Trade`.

**Files to touch:**
- `execution/src/main/kotlin/.../execution/kafka/ExecutionEventPublisher.kt` — Create: `KafkaTemplate<String, Any>` wrapper with `publishTradeExecuted(trade: Trade)` method; key = `trade.orderId.toString()`, topic = `executions`
- `execution/src/main/kotlin/.../execution/ExecutionService.kt` — Modify: inject `ExecutionEventPublisher`; call `publishTradeExecuted` after creating `Trade`
- `execution/src/main/resources/application.yml` — Modify: add Kafka producer config (`JsonSerializer`)

**Test description:** `@EmbeddedKafka` integration test — publish `ExecutionRequested`; consume from `executions` topic; assert `TradeExecuted` arrives with `trade.orderId == order.id`.

**Status:** [ ] todo

---

### Slice 3: Price Simulation

**What it delivers:** `ExecutionService.execute()` generates a realistic fill price: `executedPrice = basePrice × (1.0 + randomFactor)` where `randomFactor ∈ [-0.02, +0.02]`. Base price is injected via `@Value("${execution.base-price}")`.

**Files to touch:**
- `execution/src/main/kotlin/.../execution/ExecutionService.kt` — Modify: inject `@Value("\${execution.base-price}") val basePrice: BigDecimal`; use `ThreadLocalRandom.current().nextDouble(-0.02, 0.02)` to compute fill price; scale to 2 decimal places
- `execution/src/main/resources/application.yml` — Modify: add `execution.base-price: "100.00"`
- `execution/src/test/resources/application.yml` — Modify: add `execution.base-price: "100.00"`

**Test description:** Unit test for `ExecutionService.execute()`:
- Call `execute(order)` 100 times; assert every `trade.executedPrice` is within `[basePrice × 0.98, basePrice × 1.02]`
- Assert `trade.orderId == order.id`
- Assert `trade.id` is not null and unique across calls
- Assert `trade.executedAt` is not null and close to `Instant.now()`

**Status:** [ ] todo

---

### Slice 4: End-to-End Integration Test

**What it delivers:** A single `@EmbeddedKafka` `@SpringBootTest` test that validates the complete flow: `ExecutionRequested` in → `TradeExecuted` out with all fields correct.

**Files to touch:**
- Test class only — no production code changes

**Test description:** `@EmbeddedKafka` + `@SpringBootTest` — publish `ExecutionRequested` with a known order; consume from `executions`; assert:
- `TradeExecuted` arrives within timeout
- `trade.orderId == order.id`
- `trade.executedPrice` within ±2% of base price
- `trade.id` is non-null UUID
- `trade.executedAt` is non-null

**Status:** [ ] todo
