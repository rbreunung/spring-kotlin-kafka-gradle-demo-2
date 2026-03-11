# Document Registry

**Agents:** Read this file before allocating any new ID. Update it immediately after allocation.

ID format: `TYPE-NNN` (zero-padded to 3 digits, e.g., `FEAT-001`).
Types: `FEAT`, `BUG`, `PLAN`, `ADR`, `RETRO`, `RETRO-REVIEW`

Note: `PLAN-NNN` shares the same number as its parent `FEAT-NNN` (e.g., PLAN-001 belongs to FEAT-001).

| ID | Type | Title | Status |
|---|---|---|---|
