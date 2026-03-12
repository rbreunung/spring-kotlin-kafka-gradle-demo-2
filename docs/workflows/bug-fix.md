# Workflow: Bug Fix

**Trigger:** "fix a bug"
**Skill:** `bug-fix`
**Branch:** `fix/BUG-NNN-kebab-title`
**Output:** Bug report, reproduction test, fix, updated docs

---

## When to Run

When a defect, crash, or unexpected behavior is reported. The workflow has two phases: structured intake (in planning mode) and autonomous fix.

## Steps

| # | Phase | Step | Output |
|---|---|---|---|
| 1 | Intake | Structured Q&A — 8 questions | Bug details |
| 2 | Intake | Agent summarizes report for user confirmation | Confirmed report |
| 3 | Fix | Allocate BUG-NNN; create `docs/bugs/BUG-NNN-*.md` | Bug report doc |
| 4 | Fix | Create branch `fix/BUG-NNN-*` from main | Fix branch |
| 5 | Fix | Read source files (max 5); identify root cause | Hypothesis documented |
| 6 | Fix | Write failing reproduction test | Red test |
| 7 | Fix | Implement fix | Code change |
| 8 | Fix | Run full test suite — all green | Green suite |
| 9 | Fix | Commit fix + test | `fix(BUG-NNN): description` |
| 10 | Fix | Update bug report (root cause, fix summary, test reference) | Complete bug doc |
| 11 | Fix | Doc review (flag any specs or arch doc needing update) | Docs consistent |
| 12 | Fix | Opt-in retrospective — see [retrospective workflow](retrospective.md) | `RETRO-NNN-bugfix-*.md` |
| 13 | Fix | Offer PR | PR created (with confirmation) |

## Bug Report Structure

The bug report (`docs/bugs/BUG-NNN-*.md`) contains:
- Environment, reproduction steps, expected vs actual behavior
- Root cause (filled during investigation)
- Fix summary and test reference (filled after fix)

## Intake Questions

1. Brief description of the bug
2. Environment (OS, runtime version, config)
3. Steps to reproduce
4. Expected behavior
5. Actual behavior + error/stack trace
6. Affected component(s)
7. Severity (critical / high / medium / low)
8. Known workaround?

The agent asks these one at a time, then confirms the full report before proceeding.

## Commit Convention

| Event | Commit message |
|---|---|
| Fix + test | `fix(BUG-NNN): [short description] — add reproduction test` |
| Report finalized | `chore(BUG-NNN): finalize bug report` |
| Retrospective | `chore(RETRO-NNN): bug fix retrospective for BUG-NNN` |
