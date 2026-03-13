# RETRO-002: Feature Implementation — FEAT-002

Date: 2026-03-13
Workflow: feature-impl
Related: FEAT-002 — Order Service — REST API, Persistence, and Status Tracking
Duration: ~3 sessions (one interrupted mid-task by context limit)

---

## What Went Well

- **Brainstorming and architecture discussion** — the structured Q&A during the spec phase (scope selection, CQRS-lite vs monolithic service, status lifecycle granularity, cancellation scope) produced well-motivated decisions; the user understood the trade-offs and was able to make confident choices
- **Subagent-driven development** — fresh subagents per task with two-stage review (spec compliance then code quality) caught real issues before they could compound; the review loop worked as intended
- **State machine design** — the `applyTransition` guard (terminal check + `fromStatus` check) emerged from the review process and produced a robust, idempotency-safe implementation
- **Test coverage** — 41 tests across repository, command service, query service, controller, and embedded Kafka integration; all pass cleanly

---

## What Was Difficult

- **`applyTransition` missing `fromStatus` guard** — the initial implementation only guarded terminal states; invalid prior-state transitions (e.g. PENDING → EXECUTED on a duplicate `TradeExecuted`) were not caught. Required an extra code quality review loop to identify and fix.

- **Test `application.yml` incomplete** — first iteration only set `listener.auto-startup: false`; Kafka auto-configuration failed to load without full serializer config and a `bootstrap-servers` sentinel value (`localhost:9999`). Required a second iteration.

- **`trusted.packages` missing `shared.domain`** — `TradeExecuted` wraps `Trade` from `shared.domain`, so that package also needed trusting. Only caught during spec review, requiring fixes in both `application.yml` files and the spec doc.

- **`Thread.sleep` in the duplicate-event test** — used instead of Awaitility; caught in the Task 8 code quality review; required a fix and re-review cycle.

- **Context limit mid-Task 8** — the session ended while kafka files existed on disk but were not yet committed. State reconstruction at resumption required verifying file content before running tests.

- **PLAN-002 written after the implementation** — the plan document was auto-generated at the end of Task 9 as a summary rather than written before implementation began. It is missing the detail present in PLAN-001: vertical slices with "what it delivers", "files to touch", "test description", and open questions. This makes PLAN-002 a record of outcomes rather than a useful working document.

- **Constant Bash command approval** — every `./gradlew` call required manual approval in the Claude Code permission dialog, even after selecting "allow all" earlier in the session. This interrupted the implementation loop repeatedly and added significant friction to what should be an autonomous execution flow.

---

## Suggested Improvements

### 1. Plan Written Before Implementation

**Category:** Workflow Steps

**Description:** PLAN-002 was generated after all tasks were implemented (in Task 9 cleanup) rather than before implementation began. The resulting document is a summary table, not a working plan. PLAN-001 was written before implementation and contains the full slice detail (what it delivers, files to touch, test description) that makes it useful as a reference during execution. The `feature-impl` workflow should enforce plan-first ordering.

**Actionable Change:** Update `docs/workflows/feature-impl.md` to require that PLAN-NNN is written and committed before any implementation task begins. The plan should contain one entry per implementation task with: goal, files to create/modify, and expected test. The subagent-driven-development loop reads from this plan — it cannot read a plan that does not yet exist.

---

### 2. Project-Level `allowedTools` for Safe Build Commands

**Category:** Workflow Steps

**Description:** Every `./gradlew` and `git` command triggered the Claude Code approval dialog during implementation, even in contexts where the user had already granted broad approval. This broke the autonomous execution loop: subagents were blocked at every compile/test step, requiring the user to repeatedly approve identical, safe commands. The project has a `.claude/settings.json` that currently only configures plugins.

**Actionable Change:** Add an `allowedTools` block to `.claude/settings.json` pre-approving the safe, read-and-build commands used during every implementation session:

```json
{
  "enabledPlugins": {
    "superpowers@claude-plugins-official": true
  },
  "allowedTools": [
    "Bash(./gradlew *)",
    "Bash(git status*)",
    "Bash(git diff*)",
    "Bash(git log*)",
    "Bash(git add *)",
    "Bash(git commit*)",
    "Bash(git branch*)",
    "Bash(git merge-base*)",
    "Bash(find *)",
    "Bash(ls *)"
  ]
}
```

This allows build, test, and read-only git operations to run without approval while keeping destructive operations (`git push`, `git reset`, `git rm`) subject to approval.

---

### 3. Kafka Consumer Config in Test `application.yml` Template

**Category:** Spec Clarity

**Description:** The test `application.yml` required full Kafka consumer/producer serializer configuration in addition to `listener.auto-startup: false` — without it, `KafkaAutoConfiguration` fails to load even in `@DataJpaTest` and `@WebMvcTest` slices. This is a non-obvious requirement that caused an extra iteration. The feature spec listed the main `application.yml` config but did not specify the required test `application.yml` shape.

**Actionable Change:** Update `docs/features/FEAT-002-order-service.md` (and use as a template in future Kafka feature specs) to include a "Test Configuration" section alongside the main `application.yml` block, documenting the required test `application.yml` with the sentinel `bootstrap-servers: localhost:9999`, full serializer config, and `listener.auto-startup: false`.
