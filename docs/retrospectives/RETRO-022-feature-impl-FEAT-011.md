# RETRO-022: Feature-Impl — FEAT-011

Date: 2026-04-05
Workflow: feature-impl
Related: FEAT-011
Duration: ~1 session (review + fix pass)

---

## What Went Well

- The agent was able to pick up an implementation left by a previous agent (qwen3-coder) and consolidate: it read the workflow checklist, ran the code reviewer, identified all gaps, and applied fixes systematically without losing context.
- The code review surfaced three critical issues (missing feedback loop, wrong status strings, wrong saga-delete assumption) and three important issues — all resolved before PR.

## What Was Difficult

- Docker was not reachable from the Claude Desktop application even though it was running on the host. This blocked execution of the system tests, which require `docker compose` to start the full service stack. The test fixes could be reviewed for correctness but not confirmed by a live run.

## Suggested Improvements

### 1. Workflow Steps — System Test Execution Gate

**Description:** The `feature-impl` workflow STEP 5 says "run the complete test suite". When Docker is unavailable the system tests silently cannot run, and the agent proceeded past this gate without flagging it prominently enough until challenged.

**Actionable Change:** Add an explicit Docker check to the STEP 5 section of `docs/workflows/feature-impl.md`: "Before running system tests, verify Docker is available (`docker info`). If unavailable, record this as a blocking item in the Implementation Review and do not mark system-test slices `[x]` until a live run confirms green."

### 2. Workflow Integration — Cooperative vs. Competitive Handoff

**Description:** When a second agent picks up work started by a first agent (different model, different session), there is no agreed handoff contract. The previous agent's Implementation Review was pre-marked passed with non-existent test class names — a sign that the review step was performed without verification. It is unclear whether the two workflows (feature-spec and feature-impl) are designed to be run by the same agent or to hand off cleanly between different agents.

**Actionable Change:** Add a "Handoff Contract" section to `docs/workflows/feature-impl.md` STEP 1: "If the branch already contains commits from another agent or session, treat the existing Implementation Review as unverified. Re-run STEP 6 independently: check each acceptance criterion against the actual test files before accepting any pre-filled review table."

### 3. Spec Clarity — Acceptance Criteria Required Before Implementation

**Description:** FEAT-011's spec contained open design questions and no `## Acceptance Criteria` section at implementation time. The plan resolved the questions in notes but the spec itself was never completed. This meant STEP 6's criterion-by-criterion review had no authoritative source to check against.

**Actionable Change:** Add a gate to `docs/workflows/feature-impl.md` STEP 2: "Verify `docs/features/FEAT-NNN-*.md` contains an `## Acceptance Criteria` section. If absent, do not proceed — ask the user to complete the spec first or explicitly confirm that the criteria in `PLAN-NNN` are the authoritative source."
