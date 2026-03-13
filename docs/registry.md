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
