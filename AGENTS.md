# Trade Execution Platform

> Banking-domain saga orchestration demo with Kafka exactly-once and Resilience4j
> Tech Stack: Spring Boot 3 · Kotlin · Kafka · Gradle · Resilience4j

## Initialization Check

**If `docs/project-idea.md` does not exist:** run the project initialization workflow before
anything else — do not answer other questions until initialization is complete.

- Claude Code (local, Skill tool available): invoke the `init-project` skill
- Claude Code (cloud/mobile) / Opencode / other: read `docs/workflows/init-project.md` and follow the steps directly

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

**Claude Code (local, Skill tool available):** `Skill: <skill-name>` to invoke.
**Claude Code (cloud/mobile) / Opencode / other:** read `docs/workflows/<skill-name>.md` and follow the steps directly.

---

## Documentation Conventions

- All docs: Markdown, concise — omit information an agent already knows
- IDs: `FEAT-NNN`, `BUG-NNN`, `PLAN-NNN`, `ADR-NNN`, `RETRO-NNN`, `RETRO-REVIEW-NNN`
- Filenames: `TYPE-NNN-kebab-title.md` (kebab = first 3–5 words, lowercase, hyphens)
- Always read `docs/registry.md` before allocating an ID; update registry immediately after
- `## Progress` section: embedded in active workflow docs; removed when workflow completes
- Directories: created on demand — never create empty directories
- Architecture diagrams: Mermaid, embedded in `docs/arch/architecture.md`
- `docs/workflows/` contains human-readable copies of all `.claude/skills/` files. Both are authoritative — keep them in sync (see Git Conventions sync rule).

## Bash Command Style

When running build, test, or git commands:
- **Do not pipe or redirect output** — avoid `|`, `2>&1`, `&&`. Claude Code's permission system matches only the start of the command string. `./gradlew :order:test | tail -40` still starts with `./gradlew` and matches — but `cd order && ./gradlew test` starts with `cd`, which has no matching rule and triggers an approval prompt. Keep every command clean and self-contained.
- **No multiline bash strings** — avoid heredocs (`<<'EOF'`) and multiline `$()` expressions; they trigger interactive approval prompts and block autonomous execution. Use a single-line `-m "type: msg"` for commit messages.
- Run gradle and git as simple commands: `./gradlew :order:test`, not `./gradlew :order:test 2>&1 | tail -40`
- If you need multiple sequential operations, use separate Bash tool calls
- Output is automatically truncated — manual tail/grep is unnecessary
- **Final verification uses a clean build.** Use `./gradlew :module:clean :module:test`
  (not just `:module:test`) for the final verification step — prevents false passes from Gradle's task cache.
- **No writes to `/tmp`.** `/tmp` is outside the workspace and triggers permission prompts.
  Use `build/agent-debug/` for any temp or diagnostic output (already git-ignored by Gradle).

> **Note:** Do not create a `CLAUDE.md` in this project. Use this file (`AGENTS.md`) for all agent instructions — it is loaded by Claude Code, Opencode, and other agents.

---

## Pre-Slice Checklist

Run this checklist at the start of every implementation session, before writing any code:

- Re-read `AGENTS.md` (this file) to refresh all conventions
- Check `libs.versions.toml` before adding any dependency — never hardcode a version string
- Review the Bash Command Style section above; follow its restrictions throughout the session
- Batch independent file reads into a single parallel tool call rather than reading files one at a time
- Every tool call must have a description that explains its intent (e.g., "run unit tests for order module"), not just what it runs (e.g., "shell command")

**Binding workflow rule:** Invoking any workflow listed in the Available Workflows table is a commitment to complete all its steps in order. Do not skip steps — including retrospective and Offer Integration steps at the end. When invoking a named workflow, create a TodoWrite task for each numbered step before starting the first one. Mark each task complete as it finishes.

---

## Behavioral Disciplines

Apply these at all times — for workflow tasks and ad-hoc requests:

- **Design before coding.** For any non-trivial change, agree on the approach before writing implementation code. Ask questions one at a time; do not start implementation until the design is unambiguous.
- **Test before implementation.** Write a failing test first. The test must fail before the fix/feature is written. If it passes without implementation, it does not verify the right thing.
- **Verify before claiming done.** Run the tests. See the output. Do not assert "tests pass" without running them. Evidence before assertions.
- **Review before integration.** Before the Offer Integration step, check each acceptance criterion from the spec against actual test coverage.
- **Minimum change.** Implement only what the current task requires. Do not refactor surrounding code, add unrelated improvements, or speculate about future needs.
- **Never modify build toolchain versions** (Java, Gradle wrapper, Kotlin plugin) without explicit user approval. If a toolchain change is needed, state the reason and version delta and wait for confirmation before proceeding.

---

## Git Conventions

- **Main is protected.** No commits go directly to `main` from any workflow — not source code, not docs, not retros, not process changes. All work goes on a named branch and integrates via the Offer Integration step.
- Feature branch: `feat/FEAT-NNN-kebab-title` (created at spec time, covers spec + impl)
- Bug fix branch: `fix/BUG-NNN-kebab-title`
- Retro branch: `chore/retro-NNN-kebab-title`
- Retro-review branch: `chore/retro-review-NNN`
- Workflow improvement branch: `chore/workflow-kebab-topic`
- Commit format: `feat(FEAT-NNN): description` / `fix(BUG-NNN): description` / `chore: description`
- Commit after each passing test iteration — do not accumulate uncommitted work
- At workflow end: use the Offer Integration step — ask whether to open a PR/MR, merge locally, or keep on branch
- When editing any workflow skill (`.claude/skills/`), always update the corresponding `docs/workflows/` file to match. Both files must stay in sync — they serve different agents but contain identical workflow logic.
- **Must read:** `docs/workflows/git-for-agents.md` — allowed and prohibited git command patterns, branch rules, and commit discipline.
