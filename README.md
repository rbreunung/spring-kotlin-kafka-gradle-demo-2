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

## Project Structure

```
trader/
├── shared/              # Kotlin library: domain classes + Kafka event types
├── order/               # Spring Boot: REST POST /orders (port 8080)
├── risk/                # Spring Boot: Kafka consumer/producer (Resilience4j CB)
├── execution/           # Spring Boot: Kafka consumer/producer
├── settlement/          # Spring Boot: Kafka consumer/producer (Resilience4j retry)
├── notification/        # Spring Boot: Kafka consumer (log stub)
├── saga-orchestrator/   # Spring Boot: saga state machine over Kafka
├── docker-compose.yml         # Kafka only (daily dev)
├── docker-compose.full.yml    # Kafka + all services (demo/CI)
└── docs/
    ├── project-idea.md        # Project purpose and learning goals
    └── arch/architecture.md   # System architecture and Kafka topics
```

## Saga Flow

```
OrderPlaced → RiskApproved → TradeExecuted → PositionSettled → TraderNotified
```

Compensating events on failure: `RiskRejected` → `OrderRejected`, `SettlementFailed` → DLQ.

---

## Version Management

- **Plugin versions:** declared in root `build.gradle.kts` with `apply false`
- **Spring Boot + Kafka versions:** managed by Spring Boot BOM (via `org.springframework.boot` plugin)
- **Resilience4j version:** managed by Spring Cloud BOM (`dependencyManagement` in each service module)
- **BOM versions:** `gradle.properties` (`springBootVersion`, `springCloudVersion`)
