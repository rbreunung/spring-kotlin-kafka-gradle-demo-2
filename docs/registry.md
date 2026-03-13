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
| FEAT-003 | FEAT | Risk Service — Kafka Integration and Resilience4j Circuit Breaker | draft |
| PLAN-003 | PLAN | Risk Service — Kafka Integration and Resilience4j Circuit Breaker | draft |
| FEAT-004 | FEAT | Saga Orchestrator — Saga State Machine and Kafka Routing | draft |
| PLAN-004 | PLAN | Saga Orchestrator — Saga State Machine and Kafka Routing | draft |
