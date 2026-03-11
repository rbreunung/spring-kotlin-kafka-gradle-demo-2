# User Manual

This template provides structured AI-assisted workflows for feature specification, feature implementation, bug fixing, and continuous improvement. It works with Claude Code, Opencode, and any agent that supports the [Superpowers](https://github.com/obra/superpowers) plugin.

---

## Quick Start

1. **Clone the template** into your new project directory
2. **Install Superpowers** — follow the [Superpowers setup guide](https://github.com/obra/superpowers)
3. **Open an agent session** — the agent detects a new project and starts initialization automatically
4. **Answer 5 questions** — name, purpose, stack, domain, starting point
5. **Start speccing** — say "spec feature [name]" to define your first feature

> All directories are created on demand. No empty folders, no wasted context.

---

## Workflow Reference

| Workflow | Trigger | Branch | Key Outputs |
|---|---|---|---|
| Project Init | automatic (first run) | none | `docs/project-idea.md`, `docs/arch/architecture.md` |
| Feature Spec | "spec feature [name]" | `feat/FEAT-NNN-*` | Spec, arch update, impl plan |
| Feature Impl | "implement feature FEAT-NNN" | `feat/FEAT-NNN-*` | Code + tests, impl review |
| Impl Review | "review implementation FEAT-NNN" | existing branch | Review section in plan doc |
| Bug Fix | "fix a bug" | `fix/BUG-NNN-*` | Bug report, fix, repro test |
| Retro Review | "run retrospective review" | none | Workflow improvements |

**Claude Code:** skills are invoked automatically based on trigger phrases.
**Opencode / other:** reference `docs/workflows/<workflow>.md` for manual steps.

---

## Document ID System

Every document has a unique ID: `TYPE-NNN` (zero-padded, e.g., `FEAT-007`).

| Prefix | Type | Location |
|---|---|---|
| `FEAT` | Feature spec | `docs/features/` |
| `PLAN` | Implementation plan | `docs/plans/` |
| `BUG` | Bug report | `docs/bugs/` |
| `ADR` | Architecture Decision Record | `docs/arch/adr/` |
| `RETRO` | Retrospective | `docs/retrospectives/` |
| `RETRO-REVIEW` | Retrospective review | `docs/retrospectives/` |

`PLAN-NNN` shares its number with `FEAT-NNN` (e.g., PLAN-003 belongs to FEAT-003).

**Registry:** `docs/registry.md` tracks all IDs. Agents read it before allocating, update it immediately after.

---

## Git Conventions

| Branch | Pattern | Created |
|---|---|---|
| Feature (spec + impl) | `feat/FEAT-NNN-kebab-title` | At spec time |
| Bug fix | `fix/BUG-NNN-kebab-title` | At fix start |

**Commit format:**
- `feat(FEAT-NNN): description` — feature work
- `fix(BUG-NNN): description` — bug fix
- `chore: description` — docs, progress, retros

After each passing test iteration, commit immediately. Never accumulate uncommitted work.

**PR:** At workflow end, the agent offers to create a PR with a generated description. You confirm before it's created.

**Spec merge option:** After feature spec is written, you can merge the docs-only branch to main before implementation starts, or keep one branch for spec + implementation.

---

## Architecture Doc

`docs/arch/architecture.md` is the single source of architectural truth. It grows with the project:
- **During init:** skeleton with sections only
- **During feature spec:** new components and data model updates added
- **During feature impl:** corrected if implementation differed from spec
- **During bug fix:** noted if a bug revealed an architectural edge case

Significant architectural decisions get their own ADR in `docs/arch/adr/`.

---

## Retrospective System

At the end of any workflow, the agent offers an **opt-in retrospective**. If you accept:
- Answer 3 questions (what went well, what was difficult, improvement ideas)
- Result saved to `docs/retrospectives/RETRO-NNN-[type]-[id].md`

**Retrospective Review:** Run "run retrospective review" periodically (every 5–10 features). The agent aggregates all retros, identifies themes, proposes improvements, and discusses them with you before making any changes.

---

## Progress Tracking & Resumability

Each workflow doc contains a `## Progress` section at the top while work is in progress. If a session is interrupted, the agent reads this section to resume from the correct step without re-reading everything.

The progress section is removed when the workflow completes.

---

## Small LLM Tips

These workflows are designed to work on models with 128k context (e.g., Qwen 35B, Nemotron 30B):

- Each workflow step lists max 5 files to read — stick to this limit
- Keep feature specs under 150 lines; implementation plan slices under 20 lines each
- If context feels heavy, start a fresh session and let the agent resume from the progress section
- Architecture doc should stay under 100 lines — use Mermaid diagrams to compress information

---

## FAQ

**Q: Can I use this without Superpowers?**
Yes. Read the reference docs in `docs/workflows/` and follow the steps manually. The skills automate the process for agent-based execution.

**Q: What if I'm adding a feature to an existing codebase?**
Run the init workflow first to document the existing architecture. The brainstorm questions include "Is there an existing codebase?" — answer yes and describe it.

**Q: How do I update workflows after a retro review?**
The retro-review workflow handles this interactively. It proposes changes, you approve them, and the agent edits the skill files and reference docs.

**Q: Do I need to create the `docs/` folders manually?**
No — all directories are created on demand when the first document is written into them.

**Q: The spec and implementation are on the same branch. Is that OK?**
Yes. After the spec is written, the agent asks if you want to merge docs-only to main first. You can choose either approach per feature.
