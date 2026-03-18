# RETRO-014: bug-fix — BUG-001 SagaCompensationTest race condition

Date: 2026-03-18
Workflow: bug-fix (systematic-debugging)
Related: BUG-001, FEAT-008, FEAT-009, branch feat/FEAT-009-observability
Duration: ~1 session

---

## What Went Well

- Root cause was found systematically without guessing — the race window was traced precisely to the 1-second poll interval vs. the ~50ms settlement processing time
- Two distinct bugs were identified and fixed together: the test reliability issue (docker-compose delay) and the production code correctness issue (`onPositionSettled` missing step guard)
- The code reviewer caught a missing unit test for the new guard before the work was shipped — the test gap would have allowed the regression to return silently
- All fixes were minimal and surgical: 4 files changed, no collateral modification
- The new unit test mirrors the existing `duplicate SettlementFailed` test pattern exactly, keeping the test suite internally consistent

## What Was Difficult

- The race condition was not immediately obvious from the error message alone (`ConditionTimeoutException` could have many causes) — it required tracing the full message-ordering chain through `settlements` topic partitioning
- The fix required changes at two different layers (production code + test infrastructure) — easy to only do one and leave the other half-broken

## Suggested Improvements

> Max 3 improvements. Each must be actionable — point to a specific change.

### 1. [Category: Test Design]

**Description:** `SagaCompensationTest` injects `SettlementFailed` manually into the `settlements` topic while the settlement service is also running and will produce `PositionSettled` for the same trade. This creates a structural race between test infrastructure and a live service. The artificial delay is a workaround, not an elimination of the race.

**Actionable Change:** Add a note to `docs/workflows/feature-impl.md` (or a system test authoring guide) — when a system test needs to simulate a failure path, prefer configuring the service to produce the failure itself (e.g., via `simulate-failure-probability`) rather than injecting competing events into a live topic. If manual injection is unavoidable, document the race and add an artificial delay to the relevant service in `docker-compose.full.yml`.

### 2. [Category: Production Code Correctness]

**Description:** The `onPositionSettled` handler lacked a step guard, meaning a late-arriving `PositionSettled` could silently override an in-progress compensation flow (`COMPENSATION_REQUESTED → SETTLED`). The same guard pattern was already present in `onSettlementFailed` and `onTradeVoided`, but was missed here.

**Actionable Change:** When implementing new Kafka event handlers in `SagaOrchestrator`, add an explicit checklist step to `docs/workflows/feature-impl.md` — each handler that transitions saga state must guard against wrong current step, matching the established pattern of `isTerminalOrWarn` followed by a step-specific guard.

### 3. [Category: Test Coverage]

**Description:** The missing unit test for `onPositionSettled`'s step guard was only caught by the code reviewer, not by the original implementation. A guard without a negative test is incomplete coverage.

**Actionable Change:** Add to the implementation checklist in `docs/workflows/feature-impl.md` — every step guard added to a saga handler must have a corresponding negative test (seed in wrong step, send event, assert step unchanged). This is the same pattern as `duplicate SettlementFailed after COMPENSATION_REQUESTED is skipped`.

### 5. [Category: Documentation]

**Description:** The registry had no formal definition of what each status value means for each document type. This caused PLAN-009 to be left as `draft` after the spec phase was complete, and FEAT-009 to remain `in-progress` in the registry when the spec document was fully finalized. The rule — that both FEAT-NNN and PLAN-NNN registry status become `complete` once the spec is committed — existed in the user's mental model but was not written down anywhere.

**Actionable Change:** Add a Status Definitions table to `docs/registry.md` and add an explicit "Update registry status" instruction to `feature-spec.md` STEP 10, stating that both FEAT-NNN and PLAN-NNN are set to `complete` when the spec documents are committed. *(Applied in this session.)*

### 4. [Category: Workflow Compliance]

**Description:** The bug-fix workflow (`docs/workflows/bug-fix.md`) requires allocating a BUG-NNN ID, creating `docs/bugs/BUG-NNN-*.md`, and adding a registry entry as the very first step of Phase 2 — before any investigation or code change. In this session, the fix was fully implemented, code-reviewed, and retrospected before any of these artefacts were created. The omission was only caught when the workflow was reviewed after the fact.

**Actionable Change:** Add a compliance guard to `docs/workflows/bug-fix.md` at Phase 2, Step 1: before any investigation or code change, the agent must confirm that both the `docs/bugs/BUG-NNN-*.md` document exists and the registry entry is present with status `in-progress`. If either is missing, halt and create them first. This mirrors the pattern in `feature-spec.md` which requires allocating FEAT-NNN before doing anything else.
