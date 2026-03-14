# ADR-002: Testcontainers DockerCompose for E2E System Tests

Status: accepted
Date: 2026-03-14
Deciders: Claude, project owner
Related Feature: FEAT-007

---

## Context

The trade execution platform needs end-to-end system tests that verify the full order lifecycle across all 6 services. Individual module integration tests exist (using Spring's `EmbeddedKafkaBroker`) but provide no cross-service coverage.

A dedicated `:system-test` Gradle module requires a test infrastructure strategy. The tests must exercise real service behavior including Kafka message routing, saga state transitions, and Resilience4j circuit breaker/retry logic. The project already provides `docker-compose.full.yml` which starts all 6 services and Kafka in a fully configured stack.

Key constraint: Docker is required in any chosen approach (either for Kafka alone or for the full stack).

## Decision

Use Testcontainers `DockerComposeContainer` wrapping the existing `docker-compose.full.yml` to start the complete service stack for each E2E test class. Test code interacts with services via dynamically mapped ports — REST calls to `order-service` and Kafka produce/consume via `KafkaTestUtils`.

## Consequences

**Positive:**
- Reuses `docker-compose.full.yml` with no duplication of service configuration
- Tests are truly black-box: services run as production Docker images, not in-process mocks
- `:system-test` only depends on `:shared` — the module isolation rule is preserved for production services
- All Resilience4j, Kafka topic, and saga state behaviors are tested exactly as in production

**Negative / Trade-offs:**
- Docker is a hard requirement for running system tests; tests cannot run without it
- First-run startup is slow: `docker-compose build` must build all 6 service images from their Dockerfiles
- Subsequent runs use Docker layer cache but container startup still adds ~30–60s overhead per test class
- CI cold-start (no cache) will be significantly slower than unit/integration test runs

**Neutral:**
- Each test class gets its own compose stack; test classes may run in parallel with `parallelism=4`
- Test methods within a class share one stack and use unique order IDs to avoid state collision
- GitHub Actions CI must have Docker available (standard for GitHub-hosted runners)

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| EmbeddedKafka only (no service containers) | Tests individual modules in isolation; provides no cross-service coverage; already exists in module-level tests |
| Per-service Testcontainers containers | Requires duplicating service configuration (env vars, ports, startup order) that `docker-compose.full.yml` already defines correctly; more maintenance burden |
| In-process Spring contexts (all 6 services in JVM) | Violates module isolation rule (`:system-test` would depend on all 6 service modules); complex port management; services don't run with their real Docker environment |
