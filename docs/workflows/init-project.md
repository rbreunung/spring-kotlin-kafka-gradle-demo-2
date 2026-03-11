# Workflow: Project Initialization

**Trigger:** Automatically when `docs/project-idea.md` does not exist.
**Skill:** `init-project`
**Output:** `docs/project-idea.md`, `docs/arch/architecture.md`, updated `AGENTS.md`, `docs/registry.md`

---

## When to Run

Once, at the start of a new project. After this workflow completes, the workspace is ready for feature development.

## Steps

| # | Step | Output |
|---|---|---|
| 1 | Agent announces initialization | — |
| 2 | Q&A: 5 questions (name, purpose, stack, domain, starting point) | Answers in memory |
| 3 | Write `docs/project-idea.md` | Project north star doc |
| 4 | Write `docs/arch/architecture.md` skeleton | Architecture skeleton |
| 5 | Update `AGENTS.md` placeholders with real project values | Updated AGENTS.md |
| 6 | Initialize `docs/registry.md` with FEAT-000 | Registry with first entry |
| 7 | Commit all files | `chore: initialize project from template` |
| 8 | Agent announces readiness | — |

## Notes

- The `docs/arch/architecture.md` created here is a skeleton. It grows as features are specified.
- Directories are created on demand — the template does not pre-create empty folders.
- After initialization, the agent removes the "Project Initialization Template" block from `AGENTS.md`.

## Next Step

Say **"spec feature [name]"** to define your first feature.
