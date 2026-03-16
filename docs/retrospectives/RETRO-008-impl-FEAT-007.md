# RETRO-008: feature-impl — FEAT-007

Date: 2026-03-15
Workflow: feature-impl
Related: FEAT-007
Duration: ~2 sessions

---

## What Went Well

- Feature delivered completely — all 12 slices implemented, all test scenarios written, GitHub Actions workflow added, user documentation created
- Docker Compose V2 incompatibility (`withExposedService()` ambassador proxy naming) was diagnosed and fixed cleanly; the fixed-ports approach is simpler and more reliable
- `awaitKafkaConsumerGroupsReady()` was a good addition — proactively solving the settlement-service readiness problem that HTTP polling alone cannot detect; this saved significant flakiness in CI

## What Was Difficult

- **`HttpClientErrorException` from `RestTemplate` on 404** — `getForEntity()` throws instead of returning a non-2xx response object when the status is 4xx. This caused the awaitility poll for saga state to throw instead of returning `false`, breaking the test. Required an explicit try/catch inside the poll predicate.
- **RiskRejectionTest timing race** — the first design (inject `RiskRejected` directly via Kafka) failed because the real risk service processed the order and moved the saga forward before the poll caught `RISK_REQUESTED`. Required redesigning to use `quantity=10_001` to trigger the risk service's own auto-rejection instead of injecting a message — a more robust approach but required an extra diagnosis loop.
- **`withExposedService()` incompatibility (Docker Compose V2)** — this was a known risk from the ADR but was still encountered during implementation. The fix (remove all `withExposedService()` calls, use fixed ports) was straightforward once understood, but consumed a full debugging cycle.
- **Settlement-service readiness** — HTTP readiness checks on `/orders` and `/sagas` do not indicate that `settlement-service` is consuming Kafka messages. The first test run always failed because settlement-service was slow to join its consumer group. Required adding `awaitKafkaConsumerGroupsReady()` which was not in the original spec.

## Suggested Improvements

### 1. Spec Clarity

**Description:** The spec described `withExposedService()` for dynamic port mapping, but this is incompatible with Docker Compose V2. The incompatibility was documented in ADR-002 but was still encountered as a blocking issue during implementation. Any future spec using Testcontainers + Docker Compose should start from the fixed-ports approach.

**Actionable Change:** Update the `system-test/build.gradle.kts` template in FEAT-007 spec (and in any Testcontainers-related future spec) to lead with the fixed-ports approach. Add a prominent note at the top of the Architecture section: "Do NOT use `withExposedService()` — incompatible with Docker Compose V2. Use fixed host port bindings from `docker-compose.full.yml` directly."

### 2. Test Coverage

**Description:** The lack of an HTTP health endpoint on `settlement-service` made it impossible to know when the service was ready to consume messages using the standard `awaitServicesReady()` pattern. This caused intermittent test failures that were hard to reproduce and diagnose.

**Actionable Change:** Add `spring-boot-starter-actuator` to `settlement/build.gradle.kts` and expose `/actuator/health` in `settlement/src/main/resources/application.yml`. Update `docker-compose.full.yml` to include `settlement-service` in `awaitServicesReady()` alongside `order-service` and `saga-orchestrator`. This removes the need for the Kafka AdminClient workaround.

### 3. Workflow Steps

**Description:** The GitHub Actions workflow was added in Slice 11 but its correctness in CI (Docker availability, image caching, actual test pass/fail) cannot be verified locally. The user noted uncertainty about whether the workflow will work in CI.

**Actionable Change:** Add a `README` note (or a note in `docs/system-test-guide.md`) documenting how to manually trigger the `system-test.yml` workflow via `gh workflow run` and how to interpret the results. Also add a `workflow_dispatch` trigger to `.github/workflows/system-test.yml` so it can be triggered manually without a PR.
