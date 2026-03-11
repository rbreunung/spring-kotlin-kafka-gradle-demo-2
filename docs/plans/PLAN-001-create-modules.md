# PLAN-001: Create Gradle Multi-Module Structure — Implementation Plan

Status: draft
Date: 2026-03-12
Feature: [FEAT-001](../features/FEAT-001-create-modules.md)

## Progress

> Agent: update after each completed slice. Remove entire section when all slices done.

Current Slice: 1
Completed Slices: []
Last Updated: 2026-03-12

## Implementation Review

> Agent: fill this section during the final review step of feature-impl.

Status: pending
Reviewed: —

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `./gradlew projects` lists 7 submodules | manual `./gradlew projects` | pending |
| `./gradlew build` compiles all modules | `./gradlew build` | pending |
| `./gradlew :order:test` passes | `OrderApplicationTests.contextLoads` | pending |
| `./gradlew :shared:build` compiles | `./gradlew :shared:build` | pending |
| `:shared` has no Spring Boot plugin | build.gradle.kts inspection | pending |
| Root `src/` removed | directory check | pending |
| `docker compose up -d` starts Kafka | `docker compose ps` | pending |
| `README.md` covers both workflows | file existence check | pending |

Gaps: —

---

## Open Questions

> Resolve before starting implementation.

- [x] Module structure: service-per-module (chosen) vs layer-per-module
- [x] Deployment model: hybrid JVM-dev + full-Docker-demo (chosen)
- [x] Kafka infra: KRaft mode, `apache/kafka:3.9` image (chosen)
- [x] Version management: Spring BOM via `subprojects {}` + Version Catalog (chosen)

---

## Vertical Slices

Each slice = one testable piece of behavior. Implement and test before moving to next.

---

### Slice 1: Scaffold Gradle Multi-Module Build

**What it delivers:** Gradle recognises 7 submodules; build files compile; `./gradlew projects` lists all modules.

**Files to touch:**
- `settings.gradle.kts` — add `include(":shared", ":order", ":risk", ":execution", ":settlement", ":notification", ":saga-orchestrator")`
- `build.gradle.kts` (root) — refactor to `subprojects {}`: repositories, Kotlin plugin, Spring BOM via `io.spring.dependency-management`, common test deps; Spring Boot plugin applied only in service subprojects
- `gradle/libs.versions.toml` — Version Catalog: `resilience4j` version + `resilience4j-spring-boot3` library alias
- `shared/build.gradle.kts` — `java-library` + `kotlin("jvm")`; Jackson; NO `org.springframework.boot`
- `order/build.gradle.kts` — `org.springframework.boot`; `project(":shared")`; web, kafka
- `risk/build.gradle.kts` — `org.springframework.boot`; `project(":shared")`; kafka, `libs.resilience4j.spring.boot`
- `execution/build.gradle.kts` — `org.springframework.boot`; `project(":shared")`; kafka
- `settlement/build.gradle.kts` — `org.springframework.boot`; `project(":shared")`; kafka, `libs.resilience4j.spring.boot`
- `notification/build.gradle.kts` — `org.springframework.boot`; `project(":shared")`; kafka
- `saga-orchestrator/build.gradle.kts` — `org.springframework.boot`; `project(":shared")`; kafka

**Test description:** `./gradlew projects` prints all 7 submodule names; `./gradlew help` resolves without error.

**Status:** [ ] todo

---

### Slice 2: Migrate Application Entry Point; Create Service Main Classes

**What it delivers:** Original root `src/` removed; each of the 6 service modules has its own `@SpringBootApplication`; `./gradlew :order:test` contextLoads passes.

**Files to touch:**
- Create `order/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/order/OrderApplication.kt`
- Create `order/src/main/resources/application.yml` — `server.port: 8080`, `spring.kafka.bootstrap-servers: localhost:9092`
- Create `order/src/test/kotlin/.../order/OrderApplicationTests.kt` — `@SpringBootTest` contextLoads
- Create `risk/src/main/kotlin/.../risk/RiskApplication.kt` + `application.yml` (no server port)
- Create `execution/src/main/kotlin/.../execution/ExecutionApplication.kt` + `application.yml`
- Create `settlement/src/main/kotlin/.../settlement/SettlementApplication.kt` + `application.yml`
- Create `notification/src/main/kotlin/.../notification/NotificationApplication.kt` + `application.yml`
- Create `saga-orchestrator/src/main/kotlin/.../saga/SagaOrchestratorApplication.kt` + `application.yml`
- Delete root `src/` directory (including original `TradeExecutionPlatformApplication.kt` and test)

**Test description:** `./gradlew :order:test` — `OrderApplicationTests.contextLoads` passes with embedded Kafka (spring-kafka-test autoconfigures EmbeddedKafkaBroker).

**Status:** [ ] todo

---

### Slice 3: Create `:shared` Domain Model and Event Types

**What it delivers:** All domain data classes and Kafka event POJOs compile in `:shared`; unit test asserts data class equality.

**Files to touch:**
- `shared/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/shared/domain/Order.kt`
- `shared/src/main/kotlin/.../shared/domain/Trade.kt`
- `shared/src/main/kotlin/.../shared/domain/Position.kt`
- `shared/src/main/kotlin/.../shared/domain/Side.kt` — `enum class Side { BUY, SELL }`
- `shared/src/main/kotlin/.../shared/events/OrderPlaced.kt`
- `shared/src/main/kotlin/.../shared/events/RiskApproved.kt`
- `shared/src/main/kotlin/.../shared/events/RiskRejected.kt`
- `shared/src/main/kotlin/.../shared/events/TradeExecuted.kt`
- `shared/src/main/kotlin/.../shared/events/PositionSettled.kt`
- `shared/src/main/kotlin/.../shared/events/SettlementFailed.kt`
- `shared/src/main/kotlin/.../shared/events/TraderNotified.kt`
- `shared/src/test/kotlin/.../shared/domain/DomainModelTest.kt` — asserts data class copy/equals

**Test description:** `./gradlew :shared:test` passes; verifies `Order.copy()` produces equal instances with changed field.

**Status:** [ ] todo

---

### Slice 4: Create Service Skeleton Stubs

**What it delivers:** Each service module has an empty `@Service` stub using a type from `:shared`; `./gradlew build` compiles all 7 modules; all contextLoads tests pass.

**Files to touch:**
- `order/src/main/kotlin/.../order/OrderService.kt` — `@Service class OrderService`; imports `Order` from `:shared`
- `risk/src/main/kotlin/.../risk/RiskService.kt`
- `execution/src/main/kotlin/.../execution/ExecutionService.kt`
- `settlement/src/main/kotlin/.../settlement/SettlementService.kt`
- `notification/src/main/kotlin/.../notification/NotificationService.kt`
- `saga-orchestrator/src/main/kotlin/.../saga/SagaOrchestrator.kt`

**Test description:** `./gradlew build` — all modules compile; `./gradlew test` — all contextLoads tests pass (embedded Kafka used automatically by spring-kafka-test).

**Status:** [ ] todo

---

### Slice 5: Docker Compose + Dockerfiles + README

**What it delivers:** Kafka starts via `docker compose up -d`; each service has a multi-stage `Dockerfile`; `README.md` documents both dev workflows.

**Files to touch:**
- `docker-compose.yml` — `apache/kafka:3.9` KRaft mode, port 9092, health check
- `docker-compose.full.yml` — Kafka + 6 service images; each built from local Dockerfile
- `order/Dockerfile` — Stage 1: `gradle :order:bootJar`; Stage 2: `eclipse-temurin:17-jre-alpine` + copy JAR
- `risk/Dockerfile`, `execution/Dockerfile`, `settlement/Dockerfile`, `notification/Dockerfile`, `saga-orchestrator/Dockerfile` — same pattern
- `README.md` — quick-start: prerequisites, daily dev workflow, full Docker workflow, stopping, running tests

**Test description:** `docker compose up -d` exits 0; `docker compose ps` shows `kafka` in healthy state; `docker compose down` cleans up.

**Status:** [ ] todo
