# Architecture — Trade Execution Platform

## System Overview

```mermaid
flowchart LR
    Trader -->|REST POST /orders| OrderService
    OrderService -->|OrderPlaced| Kafka
    Kafka -->|orders| SagaOrchestrator
    SagaOrchestrator -->|RiskCheckRequested| RiskService
    RiskService -->|RiskApproved / RiskRejected| Kafka
    Kafka -->|risk-results| SagaOrchestrator
    SagaOrchestrator -->|ExecutionRequested| ExecutionService
    ExecutionService -->|TradeExecuted| Kafka
    Kafka -->|executions| SagaOrchestrator
    SagaOrchestrator -->|SettlementRequested| SettlementService
    SettlementService -->|PositionSettled / SettlementFailed| Kafka
    Kafka -->|settlements| SagaOrchestrator
    SettlementService -->|failed messages| DLQ[dlq.settlements]
    SagaOrchestrator -->|NotificationRequested| NotificationService
    NotificationService -->|TraderNotified| Kafka
```

## Services

| Service | Role |
|---|---|
| `OrderService` | Accepts REST order submissions (port 8080); persists orders in H2 (Spring Data JPA); publishes `OrderPlaced` / `OrderCancelled`; consumes `RiskApproved`, `RiskRejected`, `TradeExecuted`, `PositionSettled`, `SettlementFailed` to update order status |
| `SagaOrchestrator` | Stateful orchestrator (port 8085); H2 + JPA saga state; drives saga steps; REST observability (`GET /sagas`); compensation deferred |
| `RiskService` | Kafka-only; consumes `RiskCheckRequested`; quantity-based approval rule; Resilience4j CB wrapping simulated external call |
| `ExecutionService` | Simulates trade execution on exchange; publishes `TradeExecuted` |
| `SettlementService` | Updates positions; transactional Kafka producer; Resilience4j retry + bulkhead |
| `NotificationService` | Sends trader notification (log stub) |
| `SystemTest` | E2E test module using Testcontainers with real Kafka for end-to-end verification of all use cases |

## Kafka Topics

| Topic | Producer | Consumer | Notes |
|---|---|---|---|
| `orders` | OrderService | SagaOrchestrator | Order intake (`OrderPlaced`, `OrderCancelled`) |
| `risk-checks` | SagaOrchestrator | RiskService | Risk evaluation requests (`RiskCheckRequested`) |
| `risk-results` | RiskService | SagaOrchestrator, OrderService | Risk approve/reject (`RiskApproved`, `RiskRejected`) |
| `execution-requests` | SagaOrchestrator | ExecutionService | Execution requests (`ExecutionRequested`) |
| `executions` | ExecutionService | SagaOrchestrator, OrderService, SettlementService | Trade fills (`TradeExecuted`) |
| `settlement-requests` | SagaOrchestrator | SettlementService | Settlement requests (`SettlementRequested`) |
| `settlements` | SettlementService | SagaOrchestrator, OrderService | Settlement outcomes (`PositionSettled`, `SettlementFailed`) |
| `notifications` | SagaOrchestrator | NotificationService | Trader alerts (`NotificationRequested`) |
| `dlq.settlements` | Spring Kafka DLT | Manual review consumer | Poison-pill + retry exhaustion |

## Resilience4j Usage

| Component | Pattern | Purpose |
|---|---|---|
| RiskService client | Circuit Breaker | Open circuit when risk API error rate > threshold |
| SettlementService | Retry | Retry transient settlement failures (3 attempts, exponential backoff) |
| SettlementService | Bulkhead (ThreadPool) | Limit concurrent settlement calls |

## Data Model

### Shared domain (`:shared`)

```kotlin
data class Order(val id: UUID, val traderId: String, val symbol: String, val quantity: Int, val side: Side)
data class Trade(val id: UUID, val orderId: UUID, val executedPrice: BigDecimal, val executedAt: Instant)
data class Position(val traderId: String, val symbol: String, val quantity: Int, val avgCost: BigDecimal)
enum class Side { BUY, SELL }
```

### Shared Kafka events (`:shared`)

| Event | Published by | Consumed by |
|---|---|---|
| `OrderPlaced(order)` | OrderService | SagaOrchestrator |
| `OrderCancelled(orderId)` | OrderService | SagaOrchestrator |
| `RiskCheckRequested(order)` | SagaOrchestrator | RiskService |
| `RiskApproved(orderId)` | RiskService | SagaOrchestrator, OrderService |
| `RiskRejected(orderId, reason)` | RiskService | SagaOrchestrator, OrderService |
| `ExecutionRequested(order)` | SagaOrchestrator | ExecutionService (future) |
| `TradeExecuted(trade)` | ExecutionService | SagaOrchestrator, OrderService, SettlementService |
| `SettlementRequested(trade)` | SagaOrchestrator | SettlementService (future) |
| `PositionSettled(tradeId, position)` | SettlementService | SagaOrchestrator, OrderService |
| `SettlementFailed(tradeId, reason)` | SettlementService | SagaOrchestrator, OrderService |
| `NotificationRequested(traderId, orderId, message)` | SagaOrchestrator | NotificationService (future) |
| `TraderNotified(traderId, orderId, message)` | NotificationService | — |

## Gradle Module Layout

```
:shared            — Kotlin library (no Spring Boot): domain classes + Kafka event types
:order             — Spring Boot app: REST API (port 8080), Spring Data JPA (H2), Kafka producer + consumer
:risk              — Spring Boot app: Kafka consumer/producer
:execution         — Spring Boot app: Kafka consumer/producer
:settlement        — Spring Boot app: Kafka consumer/producer
:notification      — Spring Boot app: Kafka consumer/producer
:saga-orchestrator — Spring Boot app: Kafka consumer/producer (orchestrates saga)
:system-test       — Test module: E2E tests using Testcontainers (requires Docker)
```

Module dependency rule: all service modules depend on `:shared` only; no cross-service compile dependencies.

Version management:
- Spring BOM applied via `subprojects {}` in root `build.gradle.kts` — no Spring/Kafka versions in submodule files
- `gradle/libs.versions.toml` (Version Catalog) — Resilience4j and other non-BOM versions

## Local Infrastructure

| Component | Image | Port | Notes |
|---|---|---|---|
| Kafka | `apache/kafka:3.9` (KRaft) | 9092 | No Zookeeper needed |

- `docker-compose.yml` — Kafka only (daily dev; services run on JVM)
- `docker-compose.full.yml` — Kafka + all 6 services (demo/CI; each service built from `Dockerfile`)

## Key Flows

### Full Order Lifecycle (Happy Path)

```mermaid
sequenceDiagram
    participant Trader
    participant OrderService
    participant SagaOrchestrator
    participant RiskService
    participant ExecutionService
    participant SettlementService
    participant NotificationService

    Trader->>OrderService: POST /orders
    OrderService->>OrderService: persist Order (PENDING)
    OrderService-->>Kafka: OrderPlaced

    Kafka-->>SagaOrchestrator: OrderPlaced
    SagaOrchestrator->>SagaOrchestrator: persist RISK_REQUESTED
    SagaOrchestrator-->>Kafka: RiskCheckRequested

    Kafka-->>RiskService: RiskCheckRequested
    RiskService->>RiskService: evaluate risk (external call via CB)
    RiskService-->>Kafka: RiskApproved

    Kafka-->>SagaOrchestrator: RiskApproved
    SagaOrchestrator->>SagaOrchestrator: persist RISK_APPROVED → EXECUTION_REQUESTED
    SagaOrchestrator-->>Kafka: ExecutionRequested
    Kafka-->>OrderService: RiskApproved → update order status

    Kafka-->>ExecutionService: ExecutionRequested
    ExecutionService->>ExecutionService: simulate trade fill
    ExecutionService-->>Kafka: TradeExecuted

    Kafka-->>SagaOrchestrator: TradeExecuted
    SagaOrchestrator->>SagaOrchestrator: persist EXECUTION_COMPLETE → SETTLEMENT_REQUESTED
    SagaOrchestrator-->>Kafka: SettlementRequested
    Kafka-->>OrderService: TradeExecuted → update order status

    Kafka-->>SettlementService: SettlementRequested
    SettlementService->>SettlementService: update position (retry + bulkhead)
    SettlementService-->>Kafka: PositionSettled

    Kafka-->>SagaOrchestrator: PositionSettled
    SagaOrchestrator->>SagaOrchestrator: persist SETTLED
    SagaOrchestrator-->>Kafka: NotificationRequested
    Kafka-->>OrderService: PositionSettled → update order status

    Kafka-->>NotificationService: NotificationRequested
    NotificationService-->>Kafka: TraderNotified
```

## Key Design Decisions

| ID | Decision | Status |
|---|---|---|
| [ADR-001](adr/ADR-001-saga-state-as-recovery-anchor.md) | Saga state entity is the authoritative recovery anchor for future compensation logic | accepted |

## Technology Decisions

| Decision | Choice | Reason |
|---|---|---|
| Build | Gradle Kotlin DSL | Idiomatic with Kotlin codebase |
| Module structure | Gradle multi-module, service-per-module | Compile-time boundaries mirror microservice topology |
| Version management | Spring BOM + Version Catalog | No version numbers scattered across submodule files |
| Messaging | Spring Kafka | Native Spring Boot integration |
| Resilience | Resilience4j | Lightweight, annotation-driven, Spring Boot starter |
| Serialization | JSON (Jackson) | Simple; swap for Avro/Protobuf in a future feature |
| Persistence | H2 in-memory (Spring Data JPA) | Structured, queryable order state for status tracking; swap for PostgreSQL in production |
| Infra | Docker Compose (KRaft) | Single-container Kafka, no Zookeeper dependency |
| Dev workflow | JVM services + Docker Kafka | Fast iteration; full Docker available via `docker-compose.full.yml` |
