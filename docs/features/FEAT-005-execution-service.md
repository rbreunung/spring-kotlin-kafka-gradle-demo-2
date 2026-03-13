# FEAT-005: Execution Service — Kafka Integration and Trade Simulation

Status: draft
Date: 2026-03-13
Author: Claude

---

## Context & Motivation

FEAT-003 implemented the RiskService and FEAT-004 spec'd the SagaOrchestrator which stub-produces `ExecutionRequested` after risk approval. Without a real ExecutionService consumer, the saga cannot advance past `EXECUTION_REQUESTED` — no `TradeExecuted` event is ever published, so orders remain stuck in that state.

This feature wires up the ExecutionService: it consumes `ExecutionRequested`, simulates a trade fill with a realistic random price, and publishes `TradeExecuted`. After this feature and FEAT-004, an order placed via `POST /orders` can flow all the way from `RISK_APPROVED` through `EXECUTED` status.

The ExecutionService is deliberately simple — no persistence, no resilience patterns, stateless Kafka in/out — making it a clean, testable link in the saga chain.

## Goals

- [ ] Kafka consumer on `execution-requests` topic: consume `ExecutionRequested(order: Order)`
- [ ] Simulate trade execution: generate a `Trade` with `executedPrice = basePrice × (1.0 + randomFactor)` where `randomFactor ∈ [-0.02, +0.02]`; base price configurable via `execution.base-price: 100.00`
- [ ] Kafka producer on `executions` topic: publish `TradeExecuted(trade: Trade)`
- [ ] Integration tests with embedded Kafka covering the full consumer-to-producer flow

## Non-Goals

- No Resilience4j patterns (none specified in architecture for ExecutionService)
- No persistence (stateless; `Trade` ID generated per request)
- No REST API
- No real exchange connectivity or order book simulation
- No partial fills (one request → one fill)
- No authentication or authorisation
- No idempotency / dedup

## Architecture

### Component Design

```
execution-requests topic
        │
        ▼
ExecutionKafkaListener
        │  (ExecutionRequested)
        ▼
ExecutionService.execute(order)
        │
        ├── generate Trade: id = UUID.randomUUID()
        │                   orderId = order.id
        │                   executedPrice = basePrice × (1.0 + rand[-0.02, +0.02])
        │                   executedAt = Instant.now()
        ▼
ExecutionEventPublisher
        │
        ▼
executions topic  →  TradeExecuted(trade)
```

### Kafka

**Consumer** (group-id `execution-service`):

| Topic | Event | Handler |
|---|---|---|
| `execution-requests` | `ExecutionRequested(order)` | `ExecutionKafkaListener.onExecutionRequested()` |

**Producer** (key = `orderId.toString()`):

| Topic | Event | Trigger |
|---|---|---|
| `executions` | `TradeExecuted(trade)` | Always — every `ExecutionRequested` results in a fill |

Deserialization uses the same pattern as FEAT-003: `JsonDeserializer` with `__TypeId__` headers + `ErrorHandlingDeserializer`.

## Data Model

No new shared events. `ExecutionRequested` and `TradeExecuted` are already defined in `:shared`.

## API Surface / Interface

| Interface | Type | Description |
|---|---|---|
| `execution-requests` topic | Kafka consumer | Receives `ExecutionRequested(order)` |
| `executions` topic | Kafka producer | Publishes `TradeExecuted(trade)` |
| `execution.base-price` | Spring property | `BigDecimal`; base price for simulated fills; default `100.00` |

## Edge Cases & Error Handling

| Scenario | Expected Behavior |
|---|---|
| Kafka deserialization fails (poison message) | `ErrorHandlingDeserializer` logs and skips — no `TradeExecuted` published |
| `ExecutionRequested` arrives with any order | Always results in a fill — no rejection path in ExecutionService |
| Duplicate `ExecutionRequested` for same orderId | Two `TradeExecuted` events published (no dedup — SagaOrchestrator and OrderService guard against duplicate transitions) |

## Configuration

### `execution/src/main/resources/application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: execution-service
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true

execution:
  base-price: "100.00"
```

### `execution/src/test/resources/application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9999
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: execution-service
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true
    listener:
      auto-startup: false

execution:
  base-price: "100.00"
```

## Acceptance Criteria

- [ ] `./gradlew :execution:test` — all tests pass
- [ ] Consuming `ExecutionRequested` with any order → `TradeExecuted` published to `executions` topic with matching `orderId`
- [ ] `TradeExecuted.trade.executedPrice` is within `[basePrice × 0.98, basePrice × 1.02]` (±2% of configured base price)
- [ ] `TradeExecuted.trade.id` is a valid non-null UUID
- [ ] `TradeExecuted.trade.orderId` matches `ExecutionRequested.order.id`
- [ ] Kafka poison message (malformed JSON) → logged and skipped; no exception propagated; no `TradeExecuted` published

## Files to Create / Modify

| Path | Action |
|---|---|
| `execution/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/execution/kafka/ExecutionKafkaListener.kt` | Create |
| `execution/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/execution/kafka/ExecutionEventPublisher.kt` | Create |
| `execution/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/execution/ExecutionService.kt` | Modify — add price simulation; inject publisher |
| `execution/src/main/resources/application.yml` | Modify — add Kafka config + `execution.base-price` |
| `execution/src/test/resources/application.yml` | Create — sentinel Kafka config |

## Implementation Notes

> Agent: fill this section during feature-impl if implementation differs from spec.

## Related Docs

- [FEAT-003: Risk Service](FEAT-003-risk-service.md)
- [FEAT-004: Saga Orchestrator](FEAT-004-saga-orchestrator.md)
- [FEAT-006: Settlement Service](FEAT-006-settlement-service.md)
- [Architecture](../arch/architecture.md)
- [PLAN-005: Implementation Plan](../plans/PLAN-005-execution-service.md)
