# System Test Guide

## Overview

The `:system-test` Gradle module contains end-to-end tests that exercise the full trader platform running in Docker. Tests verify the entire Kafka-driven saga flow from order placement through settlement.

## Prerequisites

- **Docker Desktop** (or Docker Engine with Docker Compose V2) must be running
- **JDK 17** and **Gradle** (wrapper included)
- At least 8 GB RAM available for Docker

## Running the Tests

**Run all system tests:**
```
./gradlew :system-test:test
```

**Run a single test class:**
```
./gradlew :system-test:test --tests "de.antrophos.demo.spring.kafka.trader.systemtest.HappyPathSagaFlowTest"
```

**Run with full output:**
```
./gradlew :system-test:test --info
```

## What Happens at Startup

`SystemTestBase.setup()` runs once before any test class:

1. Checks Docker is available — aborts with a clear error if not
2. Runs `docker compose up -d` using `docker-compose.full.yml` (Testcontainers `withLocalCompose(true)`)
3. Polls `GET /orders` and `GET /sagas` until both services respond HTTP 200 (up to 120 seconds)
4. Waits for Kafka consumer groups `order-service`, `saga-orchestrator`, and `settlement-service` to reach `STABLE` state (up to 120 seconds)

The first run takes **3–5 minutes** because Docker builds all service images from source. Subsequent runs are faster due to Docker layer caching.

## Test Scenarios

| Test Class | Scenario |
|---|---|
| `HappyPathSagaFlowTest` | Full order lifecycle: PENDING → RISK_APPROVED → EXECUTED → SETTLED |
| `OrderCancellationTest` | Cancel a PENDING order, verify saga is deleted and order is CANCELLED |
| `RiskRejectionTest` | Order with quantity > 10,000 is auto-rejected, saga reaches RISK_REJECTED |
| `SettlementFailureTest` | Terminal state protection: late SettlementFailed events don't corrupt SETTLED saga |
| `ConcurrentOrdersTest` | Three concurrent orders each independently reach SETTLED |
| `ResilienceScenariosTest` | System stays healthy after risk rejection, subsequent valid order settles |
| `DataConsistencyTest` | After settlement, order.tradeId == saga.tradeId (cross-service consistency) |
| `KafkaDLQTest` | Malformed JSON on `settlement-requests` is routed to `dlq.settlements` |

## Ports Used

| Service | Port |
|---|---|
| order-service | 8080 |
| saga-orchestrator | 8085 |
| Kafka | 9092 |

## Troubleshooting

**"Docker is not available"**: Start Docker Desktop and wait for it to fully initialize.

**Build takes very long**: Docker is rebuilding service images. This is normal on the first run. Subsequent runs use cached layers.

**Tests time out waiting for SETTLED**: Kafka consumer groups may not have started. The `awaitKafkaConsumerGroupsReady()` check mitigates this, but very slow hardware may need increased timeouts in `SystemTestBase`.

**Port conflicts**: If ports 8080, 8085, or 9092 are already in use on your machine, stop the conflicting processes before running system tests.

**Tests leave containers running**: If the JVM is killed, Testcontainers' Ryuk reaper will clean up containers automatically within a minute.
