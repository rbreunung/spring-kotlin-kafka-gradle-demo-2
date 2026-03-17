# PLAN-012: Java 21 Upgrade, Unit Test CI, and System Test Reliability — Implementation Plan
 
Status: complete
Date: 2026-03-17
Feature: [FEAT-012](../features/FEAT-012-java-21-unit-test-ci.md)
 
## Implementation Review
 
Status: complete
Reviewed: 2026-03-17
 
| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| All 8 `build.gradle.kts` declare Java 21 | Gradle build | complete |
| All 6 Dockerfiles use eclipse-temurin:21 | Docker build | complete |
| `system-test.yml` sets `java-version: '21'` | CI workflow file | complete |
| `unit-test.yml` runs per-module unit tests | CI workflow file | complete |
| `SystemTestBase` waits for all 5 consumer groups | `SagaCompensationTest` (system test) | complete |
| No `@MockBean`/`@SpyBean` warnings in test compilation | Kotlin compiler output clean | complete |
 
Gaps: none
 
---
 
## Open Questions
 
None — all questions resolved during feature spec session.
 
## Vertical Slices
 
### Slice 1: Java 21 — Gradle Toolchain
 
**What it delivers:** All modules compile and test with Java 21 locally and in CI.
 
**Files to touch:**
- `order/build.gradle.kts` — `JavaLanguageVersion.of(17)` → `of(21)`
- `risk/build.gradle.kts` — same
- `execution/build.gradle.kts` — same
- `settlement/build.gradle.kts` — same
- `notification/build.gradle.kts` — same
- `saga-orchestrator/build.gradle.kts` — same
- `shared/build.gradle.kts` — same
- `system-test/build.gradle.kts` — same
 
**Test description:** `./gradlew build -x :system-test:test` passes with a Java 21 JDK.
 
**Status:** [x] done
 
---
 
### Slice 2: Java 21 — Docker Images
 
**What it delivers:** Service containers built from Java 21 base images, consistent with the Gradle toolchain version.
 
**Files to touch:**
- `order/Dockerfile` — `eclipse-temurin:17-jdk-alpine` → `21-jdk-alpine`; `17-jre-alpine` → `21-jre-alpine`
- `risk/Dockerfile` — same
- `execution/Dockerfile` — same
- `settlement/Dockerfile` — same
- `notification/Dockerfile` — same
- `saga-orchestrator/Dockerfile` — same
 
**Test description:** `docker build` succeeds for any service module and the resulting image starts correctly.
 
**Status:** [x] done
 
---
 
### Slice 3: Java 21 — GitHub Actions system-test.yml
 
**What it delivers:** System test CI job uses JDK 21 to match the project toolchain.
 
**Files to touch:**
- `.github/workflows/system-test.yml` — step name and `java-version` updated to `'21'`
 
**Test description:** GitHub Actions `System Tests` workflow completes without JDK version mismatch warnings.
 
**Status:** [x] done
 
---
 
### Slice 4: Unit Test CI Workflow
 
**What it delivers:** Per-module unit tests run automatically on every push and PR, providing fast feedback without Docker.
 
**Files to touch:**
- `.github/workflows/unit-test.yml` — new file; runs `./gradlew test -x :system-test:test`
 
**Test description:** GitHub Actions `Unit Tests` workflow appears on PRs and passes for all 7 non-system-test modules.
 
**Status:** [x] done
 
---
 
### Slice 5: System Test Reliability — Consumer Group Readiness Fix
 
**What it delivers:** `SagaCompensationTest` no longer times out because all five consumer groups on the compensation path are confirmed stable before any test runs.
 
**Files to touch:**
- `system-test/src/test/kotlin/.../SystemTestBase.kt` — add `risk-service` and `execution-service` to `requiredGroups`
 
**Test description:** `SagaCompensationTest` passes consistently; `awaitKafkaConsumerGroupsReady` blocks until `order-service`, `risk-service`, `execution-service`, `settlement-service`, and `saga-orchestrator` are all in `STABLE` state.

**Status:** [x] done

---

### Slice 6: Replace Deprecated @MockBean / @SpyBean

**What it delivers:** Test compilation produces no `@MockBean`/`@SpyBean` deprecation warnings; project follows the Spring Boot 3.4+ annotation convention going forward.

**Files to touch:**
- `execution/src/test/.../ExecutionKafkaListenerTest.kt` — `@SpyBean` → `@MockitoSpyBean`, update import
- `settlement/src/test/.../BulkheadFallbackTest.kt` — `@SpyBean` → `@MockitoSpyBean`, update import
- `settlement/src/test/.../SettlementKafkaListenerTest.kt` — `@SpyBean` → `@MockitoSpyBean`, update import
- `settlement/src/test/.../PositionPersistenceTest.kt` — `@MockBean` → `@MockitoBean`, update import

**Test description:** `./gradlew clean build -x :system-test:test --warning-mode=all` produces no `@MockBean`/`@SpyBean` deprecation warnings.

**Status:** [ ] todo