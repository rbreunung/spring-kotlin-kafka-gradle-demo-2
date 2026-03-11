# Workflow: Retrospective Review

**Trigger:** "run retrospective review"
**Skill:** `retro-review`
**Branch:** none (commits to current branch or main)
**Output:** Improvements to workflows/docs, `RETRO-REVIEW-NNN.md`

---

## When to Run

Periodically — a good cadence is every 5–10 features or bugs, or when you notice repeated friction in the workflows. Can be run on-demand at any time.

## Steps

| # | Step | Output |
|---|---|---|
| 1 | Read all `RETRO-NNN-*.md` files (exclude review summaries) | Themes in memory |
| 2 | Identify recurring themes by category | Theme list with counts |
| 3 | Draft 2–3 actionable improvements per theme | Improvement proposals |
| 4 | Present analysis + discuss with user | Agreed improvement list |
| 5 | Apply agreed changes to skills, workflows, AGENTS.md, or feature docs; commit each logical group | Updated files |
| 6 | Write `docs/retrospectives/RETRO-REVIEW-NNN.md` | Review summary doc |
| 7 | Commit all changes | `chore(RETRO-REVIEW-NNN): [theme summary]` |

## Theme Categories

- **Context Loading** — wrong files, too many files, context overflow
- **Spec Clarity** — ambiguous requirements, missing edge cases
- **Test Coverage** — test gaps, weak assertions
- **Workflow Steps** — confusing, missing, or unnecessary steps
- **Git Flow** — branching or commit issues
- **Documentation** — too long, outdated, inconsistent
- **Other**

## What Can Be Improved

The review outcome can update any of:
- `.claude/skills/` — workflow skill files
- `docs/workflows/` — reference docs (kept in sync with skills)
- `AGENTS.md` — conventions or workflow index
- `docs/templates/` — document templates
- `docs/features/FEAT-NNN-*.md` — individual feature specs (e.g., missing context)

All improvements are **brainstormed with the user first** — no changes are applied without agreement.

## Output Doc

`docs/retrospectives/RETRO-REVIEW-NNN.md` lists:
- Which retrospectives were covered
- Themes found with examples
- Changes made and where
