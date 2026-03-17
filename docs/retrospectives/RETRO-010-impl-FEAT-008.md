# RETRO-010: feature-impl — FEAT-008

Date: 2026-03-17
Workflow: feature-impl
Related: FEAT-008
Duration: ~1 session

---

## What Went Well

- Compensation path design was clean and well-structured (CompensationRequested / TradeVoided event flow)
- Test coverage was solid — unit tests and SagaCompensationTest covered the path end-to-end
- Spec clarity was high; FEAT-008 provided enough detail to implement without ambiguity
- Commit discipline was good — changes were committed in logical, well-described steps
- Implementation ran autonomously without pointless requests to the user

## What Was Difficult

- Java toolchain was bumped from 17 to 21 without communicating or asking the user — a tool version change that should be a user decision
- Build was run as a background task with no visible status update; results were not surfaced proactively

## Suggested Improvements

> Max 3 improvements. Each must be actionable — point to a specific change.

### 1. [Category: Workflow Steps]

**Description:** The agent upgraded the Java toolchain version without asking the user and without documenting the change. Tool/build version changes are user decisions, not agent decisions.

**Actionable Change:** Add an explicit check to `docs/workflows/feature-impl.md` — before modifying any build toolchain version (Java, Gradle, Kotlin, etc.), the agent must ask the user for approval and document the decision in the relevant spec or an ADR.

### 2. [Category: Workflow Steps]

**Description:** When a temporary toolchain upgrade is needed to run tests in the current environment, the agent should restore the original version afterward unless the user explicitly approves keeping the change.

**Actionable Change:** Add a step to `docs/workflows/feature-impl.md`: if a build tool version is upgraded temporarily for test execution, restore the original version after tests pass and note the temporary change in the commit message. Only retain the upgrade if the user explicitly approves.

### 3. [Category: Documentation]

**Description:** Any tool version change (even temporary) should be documented so the user can make an informed decision. A silent change in build toolchain is hard to discover after the fact.

**Actionable Change:** Update `docs/workflows/feature-impl.md` to require that any proposed toolchain version change is recorded — either in the feature spec, a short ADR, or at minimum in the commit message — before or immediately when the change is made.
