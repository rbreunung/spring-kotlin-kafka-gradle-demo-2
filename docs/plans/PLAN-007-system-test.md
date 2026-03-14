# PLAN-007: System Test Module — Implementation Plan

Status: draft
Date: 2026-03-14
Feature: [FEAT-007](../features/FEAT-007-system-test.md)

## Progress

> Agent: update after each completed slice. Remove entire section when all slices done.

Current Slice: 1
Completed Slices: []
Last Updated: 2026-03-14

## Implementation Review

> Agent: fill this section during the final review step of feature-impl.

Status: pending
Reviewed: —

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `./gradlew :system-test:build` — module compiles | — | pending |
| `./gradlew :system-test:test` — all tests run and pass | — | pending |
| Docker check at test startup | SystemTestBase | pending |
| All 6 services start via DockerComposeContainer and happy path completes E2E | HappyPathSagaFlowTest | pending |
| All 8 test scenarios implemented | — | pending |
| GitHub Action workflow configured | — | pending |
| User documentation created | docs/system-test-guide.md | pending |
| System tests run in feature-impl workflow | — | pending |
| System tests run in bugfix workflow | — | pending |

Gaps: —

---

## Open Questions

> Resolve before starting implementation. Update or remove as answered.

- [x] Test infrastructure approach — `DockerComposeContainer` wrapping `docker-compose.full.yml` (resolved)
- [x] Test scenarios — All 8 scenarios (resolved)
- [x] Docker availability handling — Check and abort (resolved)
- [x] Execution method — `./gradlew :system-test:test` (resolved)
- [x] CI/CD integration — GitHub Action (resolved)
- [x] Test isolation — Test classes run in parallel (parallelism=4); methods within a class run sequentially with unique order IDs (resolved)

---

## Vertical Slices

Each slice = one testable piece of behavior. Implement and test before moving to next.

### Slice 1: Module Setup, Docker Check, and Compose Stack

**What it delivers:** New Gradle module `:system-test` with basic structure, Docker availability check, and `DockerComposeContainer` setup that starts the full service stack
**Files to touch:**
- `build.gradle.kts` — Add `system-test` as new subproject with required dependencies
- `system-test/build.gradle.kts` — Create module build file with Testcontainers and Awaitility dependencies (versions from Spring Boot BOM)
- `system-test/src/test/resources/application.yml` — Placeholder Kafka bootstrap-servers config
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/SystemTestBase.kt` — Create base class with: Docker check (abort with clear error if unavailable); `DockerComposeContainer` wrapping `docker-compose.full.yml`; `@BeforeAll`/`@AfterAll` lifecycle; `@DynamicPropertySource` to inject Kafka mapped port into `spring.kafka.bootstrap-servers`

**Test description:** Verify that the system-test module compiles, that `./gradlew :system-test:test` aborts with a clear error when Docker is not available, and that when Docker is available the compose stack starts and all services are healthy.

**Status:** [ ] todo

---

### Slice 2: Happy Path Saga Flow Test

**What it delivers:** End-to-end test of full order lifecycle in happy path scenario
**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/HappyPathSagaFlowTest.kt` — Create test class

**Test description:** Place an order via `POST /orders`, verify saga state transitions through all steps (RISK_REQUESTED → RISK_APPROVED → EXECUTION_REQUESTED → EXECUTION_COMPLETE → SETTLEMENT_REQUESTED → SETTLED), and verify trader notification.

**Status:** [ ] todo

---

### Slice 3: Order Cancellation Test

**What it delivers:** Test order cancellation in RISK_REQUESTED state
**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/OrderCancellationTest.kt` — Create test class

**Test description:** Place an order, verify it reaches RISK_REQUESTED state, then cancel it via `POST /orders/{id}/cancel`, verify saga state is removed.

**Status:** [ ] todo

---

### Slice 4: Risk Rejection Test

**What it delivers:** Test saga termination on risk rejection
**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/RiskRejectionTest.kt` — Create test class

**Test description:** Place an order, verify it reaches RISK_REQUESTED state, manually trigger RiskRejected via Kafka, verify saga terminates at RISK_REJECTED terminal state.

**Status:** [ ] todo

---

### Slice 5: Settlement Failure Test

**What it delivers:** Test settlement failure with DLQ verification
**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/SettlementFailureTest.kt` — Create test class

**Test description:** Place an order, verify happy path, trigger SettlementFailed via Kafka, verify saga terminates at SETTLEMENT_FAILED, verify DLQ contains the failed message.

**Status:** [ ] todo

---

### Slice 6: Concurrent Orders Test

**What it delivers:** Test saga isolation with concurrent orders
**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/ConcurrentOrdersTest.kt` — Create test class

**Test description:** Place multiple concurrent orders, verify each saga progresses independently without interference.

**Status:** [ ] todo

---

### Slice 7: Resilience Scenarios Test

**What it delivers:** Test circuit breaker, retry, and bulkhead behavior
**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/ResilienceScenariosTest.kt` — Create test class

**Test description:** Trigger RiskService circuit breaker to open, trigger SettlementService retry exhaustion, verify behavior per Resilience4j configuration.

**Status:** [ ] todo

---

### Slice 8: Data Consistency Test

**What it delivers:** Verify consistency between order status, saga state, and positions
**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/DataConsistencyTest.kt` — Create test class

**Test description:** After happy path completion, verify `GET /orders/{id}` returns correct status, `GET /sagas/{orderId}` shows correct step, and SettlementService position data is consistent.

**Status:** [ ] todo

---

### Slice 9: Kafka DLQ Test

**What it delivers:** Test poison message handling and DLQ contents
**Files to touch:**
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/KafkaDLQTest.kt` — Create test class

**Test description:** Send malformed JSON to settlements topic, verify DLQ receives it, verify no service crashes, verify message is not re-delivered.

**Status:** [ ] todo

---

### Slice 10: User Documentation

**What it delivers:** Complete user and agent documentation
**Files to touch:**
- `docs/system-test-guide.md` — Create comprehensive guide

**Test description:** Read and verify documentation covers prerequisites, execution methods, troubleshooting, and integration with workflows.

**Status:** [ ] todo

---

### Slice 11: GitHub Action Integration

**What it delivers:** CI/CD integration for automated testing
**Files to touch:**
- `.github/workflows/test.yml` — Create or update workflow

**Test description:** Verify workflow runs `./gradlew :system-test:test` on PR and merge, handles Docker availability gracefully.

**Status:** [ ] todo

---

### Slice 12: Workflow Integration

**What it delivers:** System tests run as part of feature-impl and bugfix workflows
**Files to touch:**
- Update workflow documentation to include system test verification step

**Test description:** Verify that `feature-impl` and `bugfix` workflows document running system tests as part of verification.

**Status:** [ ] todo