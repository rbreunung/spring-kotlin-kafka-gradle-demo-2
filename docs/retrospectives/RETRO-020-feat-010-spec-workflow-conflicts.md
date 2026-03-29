# RETRO-020: feature-spec — FEAT-010

Date: 2026-03-29
Workflow: feature-spec
Related: FEAT-010
Duration: ~30 minutes (partial run — broke mid-session)

---

## What Went Well

- The feature spec content (`FEAT-010`) is technically solid and fully consistent with what was implemented: component design, Kafka wiring, STOMP config, edge cases, and acceptance criteria all match the code.
- The implementation itself is complete and all unit tests pass (7/7 across 5 test classes).

## What Was Difficult

- **Files created in wrong locations.** The spec session created files in non-standard paths (`docs/superpowers/specs/`, `docs/superpowers/plans/`) rather than `docs/features/` and `docs/plans/`. Manual cleanup was required after the session.
- **Registry misalignment.** `FEAT-010` was already marked `complete` in the registry from a prior session, but the spec and plan needed to be (re)created. This created confusion about which documents to modify versus create.
- **PLAN-010 format was wrong.** The plan document was written using phases/tasks/risk-assessment/timeline instead of vertical slices per the `impl-plan-template.md`. The template was not consulted before writing.
- **RETRO-020 format was wrong.** The retrospective used a non-template structure with corporate-style sections ("Follow-up Actions", "Next Steps", "Key Learnings") and referenced "AGENTS.md" (does not exist) and "the team" (no team).

## Suggested Improvements

### 1. Workflow — read the relevant template before creating any document

**Description:** Both PLAN-010 and RETRO-020 were written without consulting their templates (`impl-plan-template.md`, `retrospective-template.md`). The result was structurally incorrect documents that had to be rewritten in a follow-up session.

**Actionable Change:** Add an explicit step at the start of STEP 8 (Write Feature Spec), STEP 10 (Write Implementation Plan), and STEP 13 (Retrospective) in `docs/workflows/feature-spec.md`: *"Read the relevant template file before writing. Do not write from memory."*

---

### 2. Workflow — verify registry status before creating documents

**Description:** The session created new spec/plan documents without checking whether they already existed or what the registry showed. This caused duplicate work and confusion about authoritative document locations.

**Actionable Change:** Add a gate at STEP 1 of `docs/workflows/feature-spec.md`: *"Before allocating a new FEAT-NNN, check whether any existing FEAT entry with a matching title exists in the registry. If an entry already exists at any status, read the existing document before deciding whether to create or update."*

---

### 3. Workflow — verify file path against `docs/` conventions before writing

**Description:** Files were created at `docs/superpowers/specs/` and `docs/superpowers/plans/` — neither directory exists in the project. The correct paths (`docs/features/`, `docs/plans/`) are visible from any directory listing but were not checked.

**Actionable Change:** Add a one-line check to STEP 8 and STEP 10 in `docs/workflows/feature-spec.md`: *"Confirm the target path exists under `docs/` before writing. Run `ls docs/` if uncertain."*
