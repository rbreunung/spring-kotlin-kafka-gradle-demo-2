# Trade Execution Platform

> Banking-domain saga orchestration demo with Kafka exactly-once and Resilience4j
> Tech Stack: Spring Boot 3 · Kotlin · Kafka · Gradle · Resilience4j

## Initialization Check

**If `docs/project-idea.md` does not exist:** run the project initialization workflow before
anything else — do not answer other questions until initialization is complete.

- Claude Code: invoke the `init-project` skill
- Opencode / other agents: read `docs/workflows/init-project.md` and follow the steps

---

## Must-Read Files

Load these into context before working on any task (if not already loaded):

1. `docs/project-idea.md` — project purpose and domain
2. `docs/arch/architecture.md` — current system architecture
3. `docs/registry.md` — document ID registry (read before allocating any ID)

---

## Available Workflows

| Workflow | Trigger Phrase | Skill | When to Use |
|---|---|---|---|
| Project Init | "initialize project" | `init-project` | First run only — when `docs/project-idea.md` is absent |
| Feature Spec | "spec feature [name]" | `feature-spec` | Before implementing any new feature |
| Feature Impl | "implement feature FEAT-NNN" | `feature-impl` | After feature spec is approved |
| Impl Review | "review implementation FEAT-NNN" | `feature-impl` | Re-check impl against spec at any time |
| Bug Fix | "fix a bug" | `bug-fix` | When a defect is reported |
| Retro Review | "run retrospective review" | `retro-review` | Periodically to improve workflows |

**Claude Code:** `Skill: <skill-name>` to invoke.
**Opencode / other:** read the corresponding `docs/workflows/<skill-name>.md` and follow steps.

---

## Documentation Conventions

- All docs: Markdown, concise — omit information an agent already knows
- IDs: `FEAT-NNN`, `BUG-NNN`, `PLAN-NNN`, `ADR-NNN`, `RETRO-NNN`, `RETRO-REVIEW-NNN`
- Filenames: `TYPE-NNN-kebab-title.md` (kebab = first 3–5 words, lowercase, hyphens)
- Always read `docs/registry.md` before allocating an ID; update registry immediately after
- `## Progress` section: embedded in active workflow docs; removed when workflow completes
- Context discipline: read max 5 files per workflow step
- Directories: created on demand — never create empty directories
- Architecture diagrams: Mermaid, embedded in `docs/arch/architecture.md`

## Git Conventions

- Feature branch: `feat/FEAT-NNN-kebab-title` (created at spec time, covers spec + impl)
- Bug fix branch: `fix/BUG-NNN-kebab-title`
- Commit format: `feat(FEAT-NNN): description` / `fix(BUG-NNN): description` / `chore: description`
- Commit after each passing test iteration — do not accumulate uncommitted work
- At workflow end: offer to create a PR with a generated title and description
