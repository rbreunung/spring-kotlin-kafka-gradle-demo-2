# Document Registry

**Agents:** Read this file before allocating any new ID. Update it immediately after allocation.

ID format: `TYPE-NNN` (zero-padded to 3 digits, e.g., `FEAT-001`).
Types: `FEAT`, `BUG`, `PLAN`, `ADR`, `RETRO`, `RETRO-REVIEW`

Note: `PLAN-NNN` shares the same number as its parent `FEAT-NNN` (e.g., PLAN-001 belongs to FEAT-001).

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
| FEAT-009 | FEAT | Observability — Micrometer Metrics and Distributed Tracing | draft |
| FEAT-010 | FEAT | Notification Service — WebSocket Push for Real-Time Order Status | draft |
| FEAT-011 | FEAT | Cancel In-Flight Orders — User-Initiated Saga Rollback | draft |
