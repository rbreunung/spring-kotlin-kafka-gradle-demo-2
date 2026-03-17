# FEAT-012: Java 21 Upgrade, Unit Test CI, and System Test Reliability
 
Status: complete
Date: 2026-03-17
Author: claude
 
---
 
## Context & Motivation
 
The project was running on Java 17 LTS. Java 21 is the next LTS release and brings virtual threads,
sequenced collections, and pattern matching improvements. Spring Boot 3.5.x and Kotlin 1.9.25 both
fully support Java 21. Upgrading keeps the project aligned with the supported LTS lifecycle.
 
Additionally, `.github/workflows/` contained only `system-test.yml` — per-module unit tests never
ran in GitHub CI, so regressions in individual services could go undetected until a system test
failure (which is slower and harder to diagnose).
 
Finally, `SagaCompensationTest` was reporting intermittent `ConditionTimeoutException` at the second
`await` (waiting for `COMPENSATION_COMPLETE`). Root-cause analysis identified that
`awaitKafkaConsumerGroupsReady()` in `SystemTestBase` only waited for `settlement-service`,
`saga-orchestrator`, and `order-service` — but `risk-service` and `execution-service` are both on
the critical path of the compensation flow and were not included in the readiness check. Services
could be still catching up when the test began injecting events.
 
## Goals
 
- [x] Bump Java toolchain from 17 to 21 in all 8 Gradle modules
- [x] Bump all 6 service Dockerfiles from `eclipse-temurin:17` to `eclipse-temurin:21`
- [x] Update the GitHub Actions `system-test.yml` workflow to JDK 21
- [x] Add a new `unit-test.yml` GitHub Actions workflow that runs per-module unit tests on every push/PR
- [x] Fix `SagaCompensationTest` intermittent timeout by adding `risk-service` and `execution-service` to the Kafka consumer group readiness check
 
## Non-Goals
 
- Adopting Java 21 language features (virtual threads, records, pattern matching) — this is a
  runtime version bump only; Kotlin source remains unchanged
- Changing the system test environment infrastructure (Docker Compose, Testcontainers approach) —
  alternative approaches are documented below for future consideration
 
## Architecture
 
This feature is a cross-cutting infrastructure change. No new services, modules, or Kafka topics are
introduced. The change touches every module's build toolchain, every service's Docker image, and the
CI configuration.
 
### Key Flows
 
No inter-service flow changes. The compensation flow that was failing is unchanged; the fix only
ensures all five consumer groups are confirmed stable before tests fire:
 
```
order-service → risk-service → execution-service → settlement-service → saga-orchestrator
```
 
All five must be `STABLE` before any test attempts to drive events through the pipeline.
 
## Data Model
 
No data model changes.
 
## API Surface / Interface
 
No API changes.
 
## Edge Cases & Error Handling
 
| Scenario | Expected Behavior |
|---|---|
| `risk-service` slow to join consumer group | `awaitKafkaConsumerGroupsReady` blocks until all 5 groups are `STABLE` (up to 120 s) |
| `execution-service` slow to join consumer group | Same — timeout extended only if overall 120 s budget is exceeded |
| Java 21 toolchain not available in CI | `actions/setup-java@v4` with `temurin` distribution provisions it automatically |
 
## Alternative Test Environment Approaches (Future Consideration)
 
These approaches were evaluated during the feature spec brainstorm but not implemented in this
feature. They are documented here for future reference.
 
### Option A: Docker Compose Healthchecks
 
Add `healthcheck:` stanzas to each service in `docker-compose.full.yml`, then start the stack with
`docker compose up --wait`. This delegates readiness gating to Docker itself rather than custom
HTTP/Kafka polling in `SystemTestBase`.
 
**Trade-offs:**
- Pro: healthchecks are declared alongside the service, easier to maintain
- Pro: eliminates the custom `awaitServicesReady()` and `awaitKafkaConsumerGroupsReady()` code in `SystemTestBase`
- Con: requires a healthcheck command for each service (HTTP probe or Kafka admin API call)
- Con: `docker compose up --wait` behavior may differ between local and CI environments
 
### Option B: Pre-Built Image Strategy
 
Build service Docker images in a dedicated CI step and cache them by content hash. The system test
job then pulls pre-built images rather than building from Dockerfiles during the test run. This
eliminates Gradle compilation and image build time from the test startup path.
 
**Trade-offs:**
- Pro: more predictable and faster system test startup
- Pro: build failures surface earlier in the pipeline
- Con: requires a separate build job and image cache management
- Con: adds pipeline complexity (build → cache → test dependency chain)
 
## Acceptance Criteria
 
- [x] `./gradlew build -x :system-test:test` compiles and passes all unit tests on Java 21
- [x] All 8 `build.gradle.kts` files declare `JavaLanguageVersion.of(21)`
- [x] All 6 Dockerfiles use `eclipse-temurin:21-jdk-alpine` and `eclipse-temurin:21-jre-alpine`
- [x] `system-test.yml` sets `java-version: '21'`
- [x] `unit-test.yml` exists and runs `./gradlew test -x :system-test:test` on push/PR
- [x] `SystemTestBase.awaitKafkaConsumerGroupsReady()` waits for all 5 consumer groups
- [x] `SagaCompensationTest` passes without intermittent timeout
 
## Implementation Notes
 
All changes were mechanical (version number replacements). No source logic was modified other than
the consumer group list in `SystemTestBase`. Kotlin 1.9.25 supports Java 21 as a compilation target;
no Kotlin version bump was required.
 
## Related Docs
 
- [FEAT-007: System Test Module](../features/FEAT-007-system-test.md)
- [ADR-002: Testcontainers DockerCompose for E2E tests](../arch/adr/ADR-002-testcontainers-dockercompose-for-e2e-tests.md)