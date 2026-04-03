# Trade Execution Platform

A banking-domain Spring Boot / Kotlin / Kafka demo modeling securities order flow with saga orchestration, Resilience4j circuit-breakers, and exactly-once Kafka transactions.

**Tech Stack:** Spring Boot 3.x · Kotlin · Apache Kafka (KRaft) · Gradle (Kotlin DSL) · Resilience4j · Docker

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17+ | Required to run services locally. [Adoptium](https://adoptium.net) |
| Docker Desktop | Any recent | Required for Kafka (and full-stack mode). Must be running. |

No other installation needed — Gradle is included via the wrapper (`./gradlew`).

---

## Daily Dev Workflow

Fast iteration: Kafka runs in Docker, services run on the local JVM with IntelliJ/hot-reload support.

**Step 1 — Start Kafka:**
```bash
docker compose up -d
```
Kafka is available at `localhost:9092`. Wait for it to become healthy:
```bash
docker compose ps   # kafka should show "healthy"
```

**Step 2 — Start services** (each in a separate terminal):
```bash
./gradlew :order:bootRun           # REST API on http://localhost:8080
./gradlew :risk:bootRun
./gradlew :execution:bootRun
./gradlew :settlement:bootRun
./gradlew :notification:bootRun
./gradlew :saga-orchestrator:bootRun
```

**Step 3 — Stop Kafka when done:**
```bash
docker compose down
```

---

## Full Docker Workflow

Everything in containers — closest to production, good for demos and CI.

**Build and start everything:**
```bash
docker compose -f docker-compose.full.yml up --build
```
- Builds all JARs and Docker images from source
- Starts Kafka + all 6 services
- `OrderService` REST API available at `http://localhost:8080`
- Services wait for Kafka to be healthy before starting

**Stop everything:**
```bash
docker compose -f docker-compose.full.yml down
```

> **Note:** Code changes require `--build` to rebuild images. Use the daily dev workflow for fast iteration.

---

## Running Tests

```bash
# All modules (uses embedded Kafka — no Docker needed)
./gradlew test

# Single module
./gradlew :order:test
./gradlew :shared:test
```

Tests use Spring Kafka's embedded broker — no external Kafka required.

---

## Observability

The platform exposes metrics and distributed traces for all six services.

### Prometheus endpoints

| Service | URL |
|---|---|
| order-service | http://localhost:8080/actuator/prometheus |
| risk-service | http://localhost:8081/actuator/prometheus |
| execution-service | http://localhost:8082/actuator/prometheus |
| settlement-service | http://localhost:8083/actuator/prometheus |
| notification-service | http://localhost:8090/actuator/prometheus |
| saga-orchestrator | http://localhost:8085/actuator/prometheus |

### Zipkin distributed tracing

Start the full stack to enable tracing:

```bash
docker compose -f docker-compose.full.yml up --build
```

Zipkin UI: http://localhost:9411

### Custom business metrics

| Metric | Type | Description |
|---|---|---|
| `orders_placed_total` | Counter | Number of orders successfully placed |
| `saga_duration_seconds` | Timer | End-to-end saga duration, tagged by `outcome` |
| `settlement_attempts_total` | Counter | Settlement attempts, tagged by `outcome` (success/failure) |

---

## Project Structure

```
trader/
├── shared/              # Kotlin library: domain classes + Kafka event types
├── order/               # Spring Boot: REST POST /orders, DELETE /orders/{id} (port 8080)
├── risk/                # Spring Boot: Kafka consumer/producer (Resilience4j CB)
├── execution/           # Spring Boot: Kafka consumer/producer
├── settlement/          # Spring Boot: Kafka consumer/producer (Resilience4j retry)
├── notification/        # Spring Boot: Kafka consumer + WebSocket/STOMP push (port 8090)
├── saga-orchestrator/   # Spring Boot: saga state machine over Kafka
├── docker-compose.yml         # Kafka only (daily dev)
├── docker-compose.full.yml    # Kafka + all services (demo/CI)
└── docs/
    ├── project-idea.md        # Project purpose and learning goals
    └── arch/architecture.md   # System architecture and Kafka topics
```

## Saga Flow

**Happy path:**
```
OrderPlaced → RiskApproved → TradeExecuted → PositionSettled → TraderNotified
```

**User-initiated cancellation** (`DELETE /orders/{id}` while order is PENDING):
```
OrderPlaced → DELETE /orders/{id} → OrderCancelled → Saga deleted
```

Compensating events on failure: `RiskRejected` → `OrderRejected`, `SettlementFailed` → DLQ.

---

## Version Management

- **Plugin versions:** declared in root `build.gradle.kts` with `apply false`
- **Spring Boot + Kafka versions:** managed by Spring Boot BOM (via `org.springframework.boot` plugin)
- **Resilience4j version:** managed by Spring Cloud BOM (`dependencyManagement` in each service module)
- **BOM versions:** `gradle.properties` (`springBootVersion`, `springCloudVersion`)
