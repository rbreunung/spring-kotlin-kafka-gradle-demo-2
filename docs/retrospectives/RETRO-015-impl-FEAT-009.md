# RETRO-015: feature-impl — FEAT-009

Date: 2026-03-18
Workflow: feature-impl
Related: FEAT-009
Duration: ~3 hours (subagent-driven, 9 slices)

---

## What Went Well

- Two-stage review (spec compliance + code quality) caught a real gap: the code quality reviewer identified that Slice 7 was missing the failure-path counter test, which the implementer had omitted. The fix was dispatched and resolved cleanly.
- No unexpected compile or test failures throughout the 9 slices — the build stayed green after each commit.

## What Was Difficult

- **Branch collision required two prompts to resolve:** The old `feat/FEAT-009-observability` branch (from the spec/plan PR) was still local. The controller agent initially tried a non-destructive workaround instead of following the user's explicit instruction to delete it. The user had to repeat the instruction before the branch was deleted and recreated.
- **Subagent tool call volume caused friction:** Implementer and reviewer subagents made many individual tool calls, resulting in a high number of permission prompts and a long interaction stream that was hard to follow.
- **Subagent tool call descriptions were not semantic:** Subagents used generic descriptions like `shell command` rather than intent-based descriptions (e.g., `run unit tests for order module`), making it difficult to understand what was happening at a glance.
- **Resilience4j fallback testability:** The `@Retry` fallback method (`settleFallback`) was `private`, which prevented direct unit testing without a Spring AOP proxy. Required a visibility change to `internal` and an extra commit.

## Suggested Improvements

### 1. Git Flow — Explicit branch-recreation instruction

**Description:** When starting implementation and the feature branch already exists locally (from a spec/plan PR), the controller defaulted to a cautious approach rather than acting on the user's clear instruction. The feature-impl workflow has no explicit step for this scenario.

**Actionable Change:** In `docs/workflows/feature-impl.md`, Step 3 (Branch Setup), add: "If the branch already exists locally and was previously used for spec/plan docs only, delete and recreate it from main: `git branch -D <branch> && git checkout -b <branch>`."

### 2. Workflow Steps — Reduce subagent tool call volume

**Description:** Implementer subagents issued many individual file reads (one per file) rather than batching them, and ran multiple separate validation commands. This inflated the interaction stream with permission prompts and made progress hard to track.

**Actionable Change:** In the implementer prompt template (`docs/workflows/subagent-driven-development/implementer-prompt.md` or the superpowers skill), add an instruction: "Batch independent file reads into a single parallel tool call. Prefer fewer, well-scoped tool calls over many small ones."

### 3. Workflow Steps — Require semantic tool call descriptions

**Description:** Subagents described tool calls with generic labels (`shell command`, `read file`) rather than intent-based descriptions that explain the goal. This made it hard for the user to decide whether to approve or deny each call.

**Actionable Change:** In the implementer prompt template, add: "Every tool call must have a description that explains what it achieves, not just what command it runs. Example: 'compile all modules to verify no missing imports' rather than 'run gradle build'."
