# Workflow: Feature Specification

**Trigger:** "spec feature [name]" or "run feature spec for [name]"
**Skill:** `feature-spec`
**Branch:** `feat/FEAT-NNN-kebab-title` (created at spec time)
**Output:** Feature spec, architecture update (+ optional ADR), implementation plan

---

## When to Run

Before implementing any new feature. The spec workflow ensures the feature is well-understood, the architecture is updated, and the implementation plan is broken into testable slices before a single line of code is written.

## Steps

| # | Step | Output |
|---|---|---|
| 1 | Allocate FEAT-NNN from `docs/registry.md` | Registry updated |
| 2 | Create git branch `feat/FEAT-NNN-*` from main | Branch ready |
| 3 | Load context (max 5 files) | Context in memory |
| 4 | Architecture brainstorm (Q&A, one question at a time) — if the feature involves infrastructure (Kafka, Docker, databases), present infra options *before* module/service design | Design direction agreed |
| 5 | Scope clarification (goals, non-goals, edge cases, criteria) | Scope locked |
| 6 | Consistency check (conflicts with existing specs?) | Conflicts resolved |
| 7 | Write `docs/features/FEAT-NNN-*.md` from template | Feature spec |
| 8 | Update `docs/arch/architecture.md`; optional ADR | Architecture current |
| 9 | Write `docs/plans/PLAN-NNN-*.md` with vertical slices | Implementation plan |
| 10 | Commit all docs | `feat(FEAT-NNN): add spec, arch update, plan` |
| 11 | Merge decision (merge spec to main now, or keep one branch?) | Branch strategy set |
| 12 | Opt-in retrospective — see [retrospective workflow](retrospective.md) | `docs/retrospectives/RETRO-NNN-spec-*.md` |

## Vertical Slice Principle

The implementation plan breaks the feature into slices where each slice:
- Delivers one testable behavior (not a layer like "all the models")
- Can be implemented and tested independently
- When done, results in passing tests and working code

## Documents Produced

| Document | Path |
|---|---|
| Feature spec | `docs/features/FEAT-NNN-kebab-title.md` |
| Implementation plan | `docs/plans/PLAN-NNN-kebab-title.md` |
| Architecture update | `docs/arch/architecture.md` (edited) |
| ADR (optional) | `docs/arch/adr/ADR-NNN-kebab-title.md` |
| Retrospective (optional) | `docs/retrospectives/RETRO-NNN-spec-FEAT-NNN.md` |

## Next Step

Say **"implement feature FEAT-NNN"** to start the implementation workflow.
