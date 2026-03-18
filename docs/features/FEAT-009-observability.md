# FEAT-009: Observability — Micrometer Metrics and Distributed Tracing

Status: in-progress
Date: 2026-03-16
Author: Claude

---

## Context & Motivation

The system has no metrics, no distributed tracing, and no correlation IDs.
Debugging multi-service Kafka flows currently requires reading logs from six
separate services and mentally joining on orderId. There is no way to measure
end-to-end order latency, track saga step durations, or identify which service
is a bottleneck.

This feature adds production-standard observability:
- **Micrometer + Prometheus** for metrics (order counts, latencies, Kafka lag)
- **Micrometer Tracing (Brave + Zipkin)** for distributed traces with automatic
  propagation across Kafka messages

After this feature, a single order can be followed as a complete trace across
all six services in the Zipkin UI, and Prometheus metrics expose business
health at a glance.

## Goals

- [ ] Add `spring-boot-starter-web` to the 4 Kafka-only service modules (risk,
      execution, settlement, notification) so the embedded HTTP server starts and
      actuator endpoints are reachable over HTTP
- [ ] Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` to
      all 6 service modules (order, risk, execution, settlement, notification,
      saga-orchestrator)
- [ ] Add `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` to all 6
      service modules for trace export to Zipkin
- [ ] Configure Micrometer Observation support for Spring Kafka (auto-propagates
      trace context in Kafka headers) via
      `spring.kafka.producer.properties.spring.kafka.observation.enabled=true`
      and `spring.kafka.listener.observation-enabled=true`
- [ ] Add Zipkin service to `docker-compose.full.yml` (image `openzipkin/zipkin:3`,
      port 9411)
- [ ] Add Zipkin service to `docker-compose.yml` (dev workflow)
- [ ] Configure each service's `application.yml`: tracing endpoint, sampling
      probability 1.0 (100% for demo), unique `spring.application.name`
- [ ] Expose Prometheus scrape endpoint at `/actuator/prometheus` on each service
- [ ] Expose health endpoint at `/actuator/health` on each service (formalized here)
- [ ] Add three custom business metrics (via `MeterRegistry`):
      - `orders.placed.total` (counter) — incremented in OrderService on
        `POST /orders` success
      - `saga.duration.seconds` (timer, tag `outcome`) — recorded in
        SagaOrchestrator when saga reaches any terminal state
      - `settlement.attempts.total` (counter, tag `outcome=success|failure`) —
        incremented in SettlementService
- [ ] System-test: `ObservabilityTest` — verify `/actuator/health` returns `UP`
      on all mapped service ports
- [ ] README: add "Observability" section with Zipkin UI URL and
      Prometheus endpoint list

## Non-Goals

- No Prometheus server or Grafana dashboard (metric endpoints exposed but not
  scraped automatically; dashboards deferred)
- No alerting rules
- No log correlation (MDC traceId/spanId injection deferred)
- No custom Kafka consumer-lag metrics (standard Spring Kafka metrics cover this)
- No trace sampling configuration beyond 100% (production tuning deferred)
- No authentication on actuator endpoints

## Architecture

### Tracing Propagation

Spring Boot 3.x with `micrometer-tracing-bridge-brave` auto-configures an
`Observation`-based tracing system. Spring Kafka 3.x supports `Observation` via:
- `KafkaTemplate` wraps sends in an observation (creates a new child span)
- `@KafkaListener` extracts trace context from Kafka message headers
  (B3 single-header format via Brave)

No manual instrumentation is needed for trace propagation — only configuration
flags to opt in.

### Component Additions (per service)

```
build.gradle.kts (order, saga-orchestrator):
  + spring-boot-starter-actuator
  + micrometer-registry-prometheus
  + micrometer-tracing-bridge-brave
  + zipkin-reporter-brave

build.gradle.kts (risk, execution, settlement, notification — Kafka-only services):
  + spring-boot-starter-web          ← required to expose actuator over HTTP
  + spring-boot-starter-actuator
  + micrometer-registry-prometheus
  + micrometer-tracing-bridge-brave
  + zipkin-reporter-brave

application.yml:
  + spring.application.name: <service-name>
  + management.tracing.sampling.probability: 1.0
  + management.zipkin.tracing.endpoint: http://localhost:9411/api/v2/spans
  + management.endpoints.web.exposure.include: health,prometheus
  + spring.kafka.producer.properties.spring.kafka.observation.enabled: true
  + spring.kafka.listener.observation-enabled: true
```

Docker services get `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans`
as an environment variable to override the `localhost` default.

### docker-compose additions

```yaml
zipkin:
  image: openzipkin/zipkin:3
  ports:
    - "9411:9411"
```

Added to both `docker-compose.yml` and `docker-compose.full.yml`.

### Service Ports (updated)

| Service | Port | Notes |
|---|---|---|
| OrderService | 8080 | existing |
| RiskService | 8081 | new — `server.port` added; requires `spring-boot-starter-web` |
| ExecutionService | 8082 | new — `server.port` added; requires `spring-boot-starter-web` |
| SettlementService | 8083 | new — `server.port` added; requires `spring-boot-starter-web` |
| NotificationService | 8084 | new — `server.port` added; requires `spring-boot-starter-web` |
| SagaOrchestrator | 8085 | existing |
| Zipkin UI | 9411 | new |
| All services | `/actuator/prometheus` | new scrape endpoint (same port as each service) |

## Data Model

No new entities or shared events. Three new metric registrations:

| Metric | Type | Tags | Registered in |
|---|---|---|---|
| `orders.placed.total` | Counter | — | `OrderCommandService` |
| `saga.duration.seconds` | Timer | `outcome` (settled/rejected/failed/compensation-complete) | `SagaOrchestrator` |
| `settlement.attempts.total` | Counter | `outcome` (success/failure) | `SettlementService` |

## API Surface / Interface

| Interface | Type | Description |
|---|---|---|
| `GET /actuator/health` | REST (all services) | `{ "status": "UP" }` |
| `GET /actuator/prometheus` | REST (all services) | Prometheus text-format metrics |
| Zipkin UI | HTTP port 9411 | Distributed trace viewer |
| Kafka headers | B3 single-header | Trace context propagated on all produced messages |

## Edge Cases & Error Handling

| Scenario | Expected Behavior |
|---|---|
| Zipkin unreachable | Services start normally; spans queued then dropped with WARN log; no impact on business flow |
| `@DataJpaTest` / `@WebMvcTest` slice with Micrometer on classpath | `management.tracing.enabled=false` in `src/test/resources/application.yml` prevents Zipkin connection attempts |
| Prometheus endpoint scrape returns error | Actuator health unaffected; metrics endpoint failure is isolated |

## Configuration

### All 6 service `build.gradle.kts` additions

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")
implementation("io.micrometer:micrometer-tracing-bridge-brave")
implementation("io.zipkin.reporter2:zipkin-reporter-brave")
```

**Additionally, for Kafka-only services** (risk, execution, settlement, notification):

```kotlin
implementation("org.springframework.boot:spring-boot-starter-web")
```

(Versions managed via Spring BOM — no explicit versions needed in submodules.)

### All 6 service `src/main/resources/application.yml` additions

For risk, execution, settlement, and notification only — add server port:

```yaml
server:
  port: <8081|8082|8083|8084>   # risk=8081, execution=8082, settlement=8083, notification=8084
```

```yaml
spring:
  application:
    name: <service-name>   # order-service | risk-service | execution-service
                           # settlement-service | notification-service | saga-orchestrator
  kafka:
    producer:
      properties:
        spring.kafka.observation.enabled: true
    listener:
      observation-enabled: true

management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
  endpoints:
    web:
      exposure:
        include: health,prometheus
```

### All 6 service `src/test/resources/application.yml` additions

```yaml
management:
  tracing:
    enabled: false   # prevents Zipkin connection attempts in unit/integration tests
```

## Acceptance Criteria

- [ ] `./gradlew build` — all modules compile and all tests pass
- [ ] `docker compose -f docker-compose.full.yml up -d` → all services start;
      `GET http://localhost:8080/actuator/health` returns `{"status":"UP"}`
- [ ] `GET http://localhost:8080/actuator/prometheus` returns Prometheus
      text-format output including `orders_placed_total`
- [ ] Place an order via `POST /orders` → Zipkin UI at `http://localhost:9411`
      shows a trace spanning at least OrderService and SagaOrchestrator
- [ ] After saga completes, `saga_duration_seconds_count` appears in
      `/actuator/prometheus` on saga-orchestrator
- [ ] System-test: `ObservabilityTest` — verify `/actuator/health` returns `UP`
      for all services via their mapped ports

## Implementation Notes

> Agent: fill this section during feature-impl if implementation differs from spec.

### Pre-existing changes on this branch (from BUG-001)

The following changes were made to files this feature also plans to modify. They must be preserved:

- **`docker-compose.full.yml`** — `SETTLEMENT_ARTIFICIAL_DELAY_MS: "3000"` is present on the
  `settlement-service` environment block. This is required for `SagaCompensationTest` reliability
  (BUG-001 fix). When adding Zipkin and `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` env vars to all
  services, do not remove this entry.

- **`saga-orchestrator/.../SagaOrchestrator.kt`** — `onPositionSettled` received a step guard
  (`entity.step != SETTLEMENT_REQUESTED → skip`) as a correctness fix (BUG-001). No action needed;
  note for context.

## Files to Create / Modify

| Path | Action |
|---|---|
| `order/build.gradle.kts` | Modify — add actuator + tracing deps |
| `risk/build.gradle.kts` | Modify |
| `execution/build.gradle.kts` | Modify |
| `settlement/build.gradle.kts` | Modify |
| `notification/build.gradle.kts` | Modify |
| `saga-orchestrator/build.gradle.kts` | Modify |
| `order/src/main/resources/application.yml` | Modify — add tracing + actuator config |
| `risk/src/main/resources/application.yml` | Modify |
| `execution/src/main/resources/application.yml` | Modify |
| `settlement/src/main/resources/application.yml` | Modify |
| `notification/src/main/resources/application.yml` | Modify |
| `saga-orchestrator/src/main/resources/application.yml` | Modify |
| `order/src/test/resources/application.yml` | Modify — disable tracing |
| `risk/src/test/resources/application.yml` | Modify |
| `execution/src/test/resources/application.yml` | Modify (or create) |
| `settlement/src/test/resources/application.yml` | Modify |
| `notification/src/test/resources/application.yml` | Modify (or create) |
| `saga-orchestrator/src/test/resources/application.yml` | Modify |
| `order/src/main/kotlin/.../order/OrderCommandService.kt` | Modify — inject `MeterRegistry`, increment counter |
| `saga-orchestrator/src/main/kotlin/.../saga_orchestrator/SagaOrchestrator.kt` | Modify — inject `MeterRegistry`, record timer on terminal transitions |
| `settlement/src/main/kotlin/.../settlement/SettlementService.kt` | Modify — inject `MeterRegistry`, increment counter |
| `docker-compose.yml` | Modify — add Zipkin service |
| `docker-compose.full.yml` | Modify — add Zipkin service + `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` env vars |
| `README.md` | Modify — add Observability section |
| `system-test/src/test/.../ObservabilityTest.kt` | Create |

## Related Docs

- [Architecture](../arch/architecture.md)
- [FEAT-003: Risk Service (Resilience4j CB)](FEAT-003-risk-service.md)
- [FEAT-006: Settlement Service (Retry + Bulkhead)](FEAT-006-settlement-service.md)
