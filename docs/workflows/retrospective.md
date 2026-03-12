# Workflow: Retrospective

**Trigger:** Offered at the end of feature-spec, feature-impl, and bug-fix workflows (opt-in). User accepts or declines.
**Skill:** `retrospective` (not yet available — follow steps below manually)
**Output:** `docs/retrospectives/RETRO-NNN-[workflow]-[related-id].md`

---

## When to Run

After completing any feature-spec, feature-impl, or bug-fix workflow. The agent offers the retrospective as the final step. The user can decline.

Also triggered explicitly: "run retrospective for FEAT-NNN" / "run retrospective for BUG-NNN"

---

## Steps

| # | Step | Output |
|---|---|---|
| 1 | Agent offers retrospective; user accepts or declines | Decision |
| 2 | Allocate RETRO-NNN from `docs/registry.md` | Registry updated |
| 3 | Agent self-reports unexpected issues encountered during the workflow | Issues list ready |
| 4 | Ask user: **What went well?** | Answer collected |
| 5 | Ask user: **What was difficult?** (agent presents its own issues; user confirms/adds) | Answer collected |
| 6 | Ask user: **Improvement suggestions?** (max 3; each must be actionable — name a file and a change) | Answers collected |
| 7 | Write `docs/retrospectives/RETRO-NNN-[workflow]-[related-id].md` from template | Retro doc |
| 8 | Update registry — mark RETRO-NNN complete | Registry updated |
| 9 | Commit: `chore(RETRO-NNN): [workflow] retrospective for [related-id]` | Committed |

---

## Agent Self-Reporting (Step 3)

Before asking the user questions, the agent lists:
- Any errors or failures it encountered during the workflow (config mistakes, failed commands, wrong assumptions)
- Any steps that required multiple iterations
- Any cases where the agent deviated from the plan

The user can confirm, correct, or add to this list during step 5.

---

## Question Format

**What went well?**
Multi-select or free text. Focus on specific moments — decisions, steps, outputs — not general praise.

**What was difficult?**
The agent presents its own issue list. The user confirms and adds anything the agent missed.

**Improvement suggestions?**
Max 3 suggestions. Each must include:
- Category: `Context Loading | Spec Clarity | Test Coverage | Workflow Steps | Git Flow | Documentation | Other`
- Description: what caused friction
- Actionable Change: exact file + what to add/remove/modify

---

## Naming Convention

| Workflow | Filename |
|---|---|
| feature-spec | `RETRO-NNN-spec-FEAT-NNN.md` |
| feature-impl | `RETRO-NNN-impl-FEAT-NNN.md` |
| Both (combined) | `RETRO-NNN-FEAT-NNN-spec-and-impl.md` |
| bug-fix | `RETRO-NNN-bugfix-BUG-NNN.md` |

---

## Important: Improvements Are Not Applied Here

Retro improvements are recorded as suggestions only. They are acted upon during **retro review** (`docs/workflows/retro-review.md`), which aggregates multiple retros, identifies themes, and proposes workflow changes for the user to approve.

---

## Template

See `docs/templates/retrospective-template.md`.
