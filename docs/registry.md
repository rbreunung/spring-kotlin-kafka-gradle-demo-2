# Document Registry

**Agents:** Read this file before allocating any new ID. Update it immediately after allocation.

ID format: `TYPE-NNN` (zero-padded to 3 digits, e.g., `FEAT-001`).
Types: `FEAT`, `BUG`, `PLAN`, `ADR`, `RETRO`, `RETRO-REVIEW`

Note: `PLAN-NNN` shares the same number as its parent `FEAT-NNN` (e.g., PLAN-001 belongs to FEAT-001).

## Status Definitions

The registry status tracks **document completeness**, not implementation progress. Implementation state is tracked inside the documents (slice checkboxes, `## Progress` section, internal `Status:` field).

| Type | Status | Meaning |
|---|---|---|
| FEAT | `draft` | Feature identified; spec not yet started |
| FEAT | `in-progress` | Spec phase is actively being written |
| FEAT | `complete` | Spec and plan documents are finalized (committed); implementation progress tracked inside PLAN-NNN |
| PLAN | `draft` | Plan document not yet written |
| PLAN | `complete` | Plan document is finalized and ready to execute |
| BUG | `in-progress` | Fix in progress |
| BUG | `resolved` | Fix shipped |
| ADR | `accepted` | Decision accepted |
| RETRO / RETRO-REVIEW | `complete` | Document written and committed |

**Key rule:** Both FEAT-NNN and PLAN-NNN registry status become `complete` at the end of the `feature-spec` workflow — when the spec and plan documents are committed. They do not change again when implementation finishes (the PLAN doc's internal state tracks that).

| ID | Type | Title | Status |
|---|---|---|---|
| FEAT-000 | FEAT | Project initialization | complete |
| FEAT-001 | FEAT | Create Gradle multi-module structure | complete |
| PLAN-001 | PLAN | Create Gradle multi-module structure | complete |
| RETRO-001 | RETRO | FEAT-001 spec and implementation | complete |
| FEAT-002 | FEAT | Order Service — REST API, Persistence, and Status Tracking | complete |
| PLAN-002 | PLAN | Order Service — REST API, Persistence, and Status Tracking | complete |
| RETRO-002 | RETRO | FEAT-002 feature-impl retrospective | complete |
| RETRO-REVIEW-001 | RETRO-REVIEW | First retro review — RETRO-001 and RETRO-002 | complete |
| FEAT-003 | FEAT | Risk Service — Kafka Integration and Resilience4j Circuit Breaker | complete |
| PLAN-003 | PLAN | Risk Service — Kafka Integration and Resilience4j Circuit Breaker | complete |
| FEAT-004 | FEAT | Saga Orchestrator — Saga State Machine and Kafka Routing | complete |
| PLAN-004 | PLAN | Saga Orchestrator — Saga State Machine and Kafka Routing | complete |
| FEAT-005 | FEAT | Execution Service — Kafka Integration and Trade Simulation | complete |
| PLAN-005 | PLAN | Execution Service — Kafka Integration and Trade Simulation | complete |
| FEAT-006 | FEAT | Settlement Service — Position Persistence and Resilience4j Retry and Bulkhead | complete |
| PLAN-006 | PLAN | Settlement Service — Position Persistence and Resilience4j Retry and Bulkhead | complete |
| FEAT-007 | FEAT | System Test Module — End-to-End E2E Tests with Testcontainers | complete |
| PLAN-007 | PLAN | System Test Module — End-to-End E2E Tests with Testcontainers | complete |
| ADR-002 | ADR | Testcontainers DockerCompose for E2E system tests | accepted |
| RETRO-003 | RETRO | Feature spec session — FEAT-003 through FEAT-006 | complete |
| RETRO-004 | RETRO | FEAT-003 implementation retrospective | complete |
| RETRO-005 | RETRO | FEAT-004 implementation retrospective | complete |
| RETRO-006 | RETRO | FEAT-005 implementation retrospective | complete |
| RETRO-007 | RETRO | FEAT-006 implementation retrospective | complete |
| RETRO-008 | RETRO | FEAT-007 implementation retrospective | complete |
| ADR-001 | ADR | Saga state entity as the authoritative recovery anchor | accepted |
| FEAT-008 | FEAT | Saga Compensation — Rollback on Settlement Failure | complete |
| PLAN-008 | PLAN | Saga Compensation — Rollback on Settlement Failure | complete |
| FEAT-009 | FEAT | Observability — Micrometer Metrics and Distributed Tracing | complete |
| PLAN-009 | PLAN | Observability — Micrometer Metrics and Distributed Tracing | complete |
| FEAT-010 | FEAT | Notification Service — WebSocket Push for Real-Time Order Status | draft |
| FEAT-011 | FEAT | Cancel In-Flight Orders — User-Initiated Saga Rollback | draft |
| RETRO-009 | RETRO | FEAT-008 feature spec retrospective | complete |
| RETRO-REVIEW-002 | RETRO-REVIEW | Retro review — RETRO-003 through RETRO-009 | complete |
| RETRO-010 | RETRO | FEAT-008 implementation retrospective | complete |
| FEAT-012 | FEAT | Java 21 Upgrade, Unit Test CI, and System Test Reliability | complete |
| PLAN-012 | PLAN | Java 21 Upgrade, Unit Test CI, and System Test Reliability | complete |
| RETRO-011 | RETRO | FEAT-012 feature spec retrospective | complete |
| RETRO-012 | RETRO | FEAT-012 implementation retrospective | complete |
| RETRO-013 | RETRO | FEAT-009 feature spec retrospective | complete |
| BUG-001 | BUG | SagaCompensationTest race condition and URL deprecation | resolved |
| RETRO-014 | RETRO | Bug-fix: BUG-001 SagaCompensationTest race condition | complete |
| RETRO-015 | RETRO | FEAT-009 implementation retrospective | complete |
| BUG-002 | BUG | Settlement service DLQ misconfigured — ByteArraySerializer/JsonSerializer mismatch causes retry loop on second order | resolved |
| RETRO-017 | RETRO | Bug-fix: BUG-002 settlement DLQ serializer mismatch | complete |
