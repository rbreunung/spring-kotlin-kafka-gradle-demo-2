# Trade Execution Platform

> A banking-domain Spring Boot / Kotlin / Kafka demo modeling securities order flow with saga orchestration, Resilience4j circuit-breakers, and exactly-once Kafka transactions.
> Tech Stack: Spring Boot 3.x · Kotlin · Apache Kafka · Gradle (Kotlin DSL) · Resilience4j · Spring Kafka

## Purpose

Demonstrates production-relevant patterns for event-driven microservices in a securities trading context. All external dependencies (risk engine, settlement network) are stubbed so the focus stays on the messaging and resilience patterns.

## Domain

Equities order routing and settlement. A trader submits a buy/sell order; the system routes it through risk approval, executes the trade on a simulated exchange, settles the position, and notifies the trader.

## Saga Flow

```
OrderPlaced → RiskApproved → TradeExecuted → PositionSettled → TraderNotified
```

Compensating events on failure:
- Risk rejected → `OrderRejected`
- Settlement failed → `SettlementFailed` → DLQ → manual review

## Learning Goals

1. **Saga orchestration** — SagaOrchestrator drives the flow; each step publishes a Kafka event consumed by the next service
2. **Resilience4j** — circuit-breaker on RiskService (simulates flaky external risk API); retry + bulkhead on SettlementService
3. **Exactly-once / transactional Kafka** — transactional producer for position update events prevents duplicate debits/credits
4. **Dead-letter queue** — failed settlement messages are routed to `dlq.settlements` with retry headers and poison-pill detection
5. **Consumer group design** — multiple consumer groups on execution topic; partition assignment strategy

## Constraints

- No real exchange or bank connectivity — all external calls are stubs with configurable failure rates
- Single deployable JAR (multi-module Gradle project internally)
- Docker Compose for local Kafka broker
