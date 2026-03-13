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
| `SagaOrchestrator` | Stateful orchestrator; drives saga steps; handles compensation |
| `RiskService` | External stub; validates order against risk limits; Resilience4j CB |
| `ExecutionService` | Simulates trade execution on exchange; publishes `TradeExecuted` |
| `SettlementService` | Updates positions; transactional Kafka producer; Resilience4j retry + bulkhead |
| `NotificationService` | Sends trader notification (log stub) |

## Kafka Topics

| Topic | Producer | Consumer | Notes |
|---|---|---|---|
| `orders` | OrderService | SagaOrchestrator | Order intake |
| `risk-results` | RiskService | SagaOrchestrator, OrderService | Risk approve/reject |
| `executions` | ExecutionService | SagaOrchestrator, OrderService, SettlementService | Multiple consumer groups |
| `settlements` | SettlementService | SagaOrchestrator, OrderService | Settlement outcomes |
| `notifications` | SagaOrchestrator | NotificationService | Trader alerts |
| `dlq.settlements` | Spring Kafka DLT | Manual review consumer | Poison-pill + retry exhaustion |

## Resilience4j Usage

| Component | Pattern | Purpose |
|---|---|---|
| RiskService client | Circuit Breaker | Open circuit when risk API error rate > threshold |
| SettlementService | Retry | Retry transient settlement failures (3 attempts, exponential backoff) |
| SettlementService | Bulkhead (ThreadPool) | Limit concurrent settlement calls |

## Data Model (stubs)

```kotlin
data class Order(val id: UUID, val traderId: String, val symbol: String, val quantity: Int, val side: Side)
data class Trade(val id: UUID, val orderId: UUID, val executedPrice: BigDecimal, val executedAt: Instant)
data class Position(val traderId: String, val symbol: String, val quantity: Int, val avgCost: BigDecimal)
enum class Side { BUY, SELL }
```

## Gradle Module Layout

```
:shared            — Kotlin library (no Spring Boot): domain classes + Kafka event types
:order             — Spring Boot app: REST API (port 8080), Spring Data JPA (H2), Kafka producer + consumer
:risk              — Spring Boot app: Kafka consumer/producer
:execution         — Spring Boot app: Kafka consumer/producer
:settlement        — Spring Boot app: Kafka consumer/producer
:notification      — Spring Boot app: Kafka consumer/producer
:saga-orchestrator — Spring Boot app: Kafka consumer/producer (orchestrates saga)
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
