# Workflow: Feature Implementation

**Trigger:** "implement feature FEAT-NNN"
**Standalone review trigger:** "review implementation FEAT-NNN"
**Skill:** `feature-impl`
**Branch:** `feat/FEAT-NNN-*` (created at spec time or now if spec was merged)
**Prerequisite:** Feature spec (`docs/features/FEAT-NNN-*.md`) and implementation plan (`docs/plans/PLAN-NNN-*.md`) must exist.

---

## When to Run

After a feature spec is complete and the implementation plan has been reviewed. The workflow implements the feature one vertical slice at a time using TDD, with tests and progress committed after each slice.

## Steps

| # | Step | Output |
|---|---|---|
| 1 | Load context (spec, plan, arch) | Context in memory |
| 2 | Pre-impl review: clarify any ambiguous slices | Plan updated if needed |
| 3 | Branch setup (switch to or create `feat/FEAT-NNN-*`) | On feature branch |
| 4 | **TDD loop** (repeat per slice): write test → implement → green → commit → update progress | Code + tests per slice |
| 5 | Run full test suite | All green |
| 6 | **Implementation review**: verify all acceptance criteria have tests; write results to plan | Review section in plan doc |
| 7 | Update architecture doc if implementation differed from spec | Arch doc current |
| 8 | Opt-in retrospective | `docs/retrospectives/RETRO-NNN-impl-*.md` |
| 9 | Offer PR | PR created (with confirmation) |

## TDD Loop Detail

For each vertical slice in the plan:
1. **Red** — write a failing test that proves the slice works
2. **Green** — implement the minimum code to pass the test
3. **Commit** — `feat(FEAT-NNN): implement [slice] with tests`
4. **Progress** — update `## Progress` in plan doc and commit

Never move to the next slice with a failing test.

## Implementation Review

At the end of all slices, the agent:
- Reads every acceptance criterion from the feature spec
- Confirms a test exists for each criterion
- Confirms all tests pass
- Writes a pass/fail table to the plan doc

If gaps are found, the agent returns to the TDD loop for the affected slices.

**The review can also be triggered standalone** at any time: "review implementation FEAT-NNN"

## Commit Convention

| Event | Commit message |
|---|---|
| Slice complete | `feat(FEAT-NNN): implement [slice name] with tests` |
| Progress update | `chore(FEAT-NNN): progress — slice N complete` |
| Review complete | `chore(FEAT-NNN): implementation review — passed` |
| Arch update | `docs(FEAT-NNN): update architecture for implementation` |
