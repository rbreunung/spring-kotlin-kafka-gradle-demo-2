# PLAN-009: Observability — Micrometer Metrics and Distributed Tracing — Implementation Plan

Status: complete
Date: 2026-03-18
Feature: [FEAT-009](../features/FEAT-009-observability.md)

## Implementation Review

Status: complete
Reviewed: 2026-03-18

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `./gradlew build` passes | Slice 1 compile check | ✅ done |
| `/actuator/health` returns UP on all 6 services | `ObservabilityTest` | ✅ done (system test created; requires Docker to execute) |
| `/actuator/prometheus` returns Prometheus text including `orders_placed_total` | `ObservabilityTest` (stretch) | deferred to manual verification |
| Zipkin trace spans OrderService + SagaOrchestrator after `POST /orders` | Manual / E2E | deferred to manual verification |
| `saga_duration_seconds_count` appears in saga-orchestrator prometheus endpoint | `ObservabilityTest` (stretch) | deferred to manual verification |

Gaps: stretch criteria deferred to manual/E2E validation; all unit tests and compile checks pass.

---

## Open Questions

> Resolved during feature-spec session 2026-03-18.

- [x] Should Kafka-only services expose actuator over HTTP? → Yes, add `spring-boot-starter-web` to risk, execution, settlement, notification
- [x] What ports for Kafka-only services? → risk:8081, execution:8082, settlement:8083, notification:8084
- [x] ObservabilityTest scope? → All 6 services

## Vertical Slices

Each slice = one testable piece of behavior. Implement and test before moving to next.

---

### Slice 1: Add observability dependencies to all 6 build files

**What it delivers:** All 6 service modules declare the 4 observability dependencies (actuator, Prometheus registry, Micrometer Tracing Brave, Zipkin reporter) plus `spring-boot-starter-web` for the 4 Kafka-only services.

**Files to touch:**
- `order/build.gradle.kts` — add actuator + tracing deps
- `risk/build.gradle.kts` — add spring-boot-starter-web + actuator + tracing deps
- `execution/build.gradle.kts` — add spring-boot-starter-web + actuator + tracing deps
- `settlement/build.gradle.kts` — add spring-boot-starter-web + actuator + tracing deps
- `notification/build.gradle.kts` — add spring-boot-starter-web + actuator + tracing deps
- `saga-orchestrator/build.gradle.kts` — add actuator + tracing deps

**Test description:** `./gradlew build` compiles without errors. No functional test needed for this slice — compilation is the gate.

**Status:** [x] done

---

### Slice 2: Configure actuator, tracing, and server ports in main application.yml (all 6 services)

**What it delivers:** Each service has `spring.application.name`, Zipkin tracing endpoint, 100% sampling, actuator endpoints exposed, Kafka observation flags, and `server.port` for the 4 Kafka-only services.

**Files to touch:**
- `order/src/main/resources/application.yml` — add actuator + tracing config
- `risk/src/main/resources/application.yml` — add server.port:8081 + actuator + tracing config
- `execution/src/main/resources/application.yml` — add server.port:8082 + actuator + tracing config
- `settlement/src/main/resources/application.yml` — add server.port:8083 + actuator + tracing config
- `notification/src/main/resources/application.yml` — add server.port:8084 + actuator + tracing config
- `saga-orchestrator/src/main/resources/application.yml` — add actuator + tracing config

**Test description:** A `@SpringBootTest` (one per service is sufficient, or pick any one service) verifies that the Spring context loads and `GET /actuator/health` returns `{"status":"UP"}`.

**Status:** [x] done

---

### Slice 3: Disable tracing in test resources (all 6 services)

**What it delivers:** Each service's `src/test/resources/application.yml` disables Micrometer Tracing so unit and integration tests do not attempt to connect to Zipkin.

**Files to touch:**
- `order/src/test/resources/application.yml` — add `management.tracing.enabled: false`
- `risk/src/test/resources/application.yml` — add `management.tracing.enabled: false`
- `execution/src/test/resources/application.yml` — add `management.tracing.enabled: false`
- `settlement/src/test/resources/application.yml` — add `management.tracing.enabled: false`
- `notification/src/test/resources/application.yml` — add `management.tracing.enabled: false`
- `saga-orchestrator/src/test/resources/application.yml` — add `management.tracing.enabled: false`

**Test description:** All existing unit tests pass (`./gradlew test`) with no Zipkin-related connection errors in the log.

**Status:** [x] done

---

### Slice 4: Add Zipkin service and port mappings to docker-compose files

**What it delivers:** Both `docker-compose.yml` and `docker-compose.full.yml` include a Zipkin service (port 9411). `docker-compose.full.yml` additionally maps ports 8081–8084 for the 4 Kafka-only services and injects `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans` into all 6 services.

**Files to touch:**
- `docker-compose.yml` — add Zipkin service
- `docker-compose.full.yml` — add Zipkin service, expose ports 8081–8084, add env var to all services

**Test description:** `docker compose -f docker-compose.full.yml config` validates without errors (dry-run, no Docker daemon needed).

**Status:** [x] done

---

### Slice 5: `orders.placed.total` counter in OrderCommandService

**What it delivers:** A Micrometer `Counter` named `orders.placed.total` is incremented each time an order is successfully persisted and the `OrderPlaced` event is published.

**Files to touch:**
- `order/src/main/kotlin/.../order/OrderCommandService.kt` — inject `MeterRegistry`, increment counter on success
- `order/src/test/kotlin/.../order/OrderCommandServiceTest.kt` — new or extended unit test

**Test description:** Unit test: inject a `SimpleMeterRegistry`, call the order placement method, assert `orders.placed.total` counter value equals 1.

**Status:** [x] done

---

### Slice 6: `saga.duration.seconds` timer in SagaOrchestrator

**What it delivers:** A Micrometer `Timer` named `saga.duration.seconds` with tag `outcome` is recorded each time the saga reaches a terminal state (SETTLED, RISK_REJECTED, COMPENSATION_COMPLETE).

**Files to touch:**
- `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/SagaOrchestrator.kt` — inject `MeterRegistry`, record timer on terminal transitions
- `saga-orchestrator/src/test/kotlin/.../saga_orchestrator/SagaOrchestratorTest.kt` — new or extended unit test

**Test description:** Unit test: inject a `SimpleMeterRegistry`, drive the orchestrator to a terminal state, assert `saga.duration.seconds` has at least one recorded sample with the correct `outcome` tag.

**Status:** [x] done

---

### Slice 7: `settlement.attempts.total` counter in SettlementService

**What it delivers:** A Micrometer `Counter` named `settlement.attempts.total` with tag `outcome` (`success` or `failure`) is incremented on each settlement attempt outcome.

**Files to touch:**
- `settlement/src/main/kotlin/.../settlement/SettlementService.kt` — inject `MeterRegistry`, increment counter with outcome tag
- `settlement/src/test/kotlin/.../settlement/SettlementServiceTest.kt` — new or extended unit test

**Test description:** Unit test: inject a `SimpleMeterRegistry`, call settlement with a success and a failure scenario, assert `settlement.attempts.total{outcome="success"}` = 1 and `settlement.attempts.total{outcome="failure"}` = 1.

**Status:** [x] done

---

### Slice 8: ObservabilityTest system test

**What it delivers:** A new `ObservabilityTest` in the `system-test` module verifies that all 6 services report `{"status":"UP"}` at their `/actuator/health` endpoint after the full Docker Compose stack starts.

**Files to touch:**
- `system-test/src/test/kotlin/.../systemtest/ObservabilityTest.kt` — create new test class

**Test description:** E2E test using the existing `SystemTestBase` Testcontainers setup. After container startup, HTTP GET to `http://localhost:{8080,8081,8082,8083,8084,8085}/actuator/health` on each service returns HTTP 200 with body containing `"status":"UP"`.

**Status:** [x] done

---

### Slice 9: README observability section

**What it delivers:** The project README gains an "Observability" section documenting Zipkin UI URL, Prometheus endpoint list per service, and how to start the full observability stack.

**Files to touch:**
- `README.md` — add Observability section

**Test description:** Doc review — no automated test. Section must include Zipkin URL (`http://localhost:9411`), all 6 Prometheus endpoint URLs, and the docker compose command to start.

**Status:** [x] done
