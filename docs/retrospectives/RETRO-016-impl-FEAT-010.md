# RETRO-016: feature-impl — FEAT-010

Date: 2026-03-20
Workflow: feature-impl
Related: FEAT-010
Duration: ~2 sessions (9 slices, single agent)

---

## What Went Well

- Clear communication throughout: findings were surfaced, discussed, and resolved collaboratively without ambiguity.
- Code review caught 10 real findings across three passes (simplicity/DRY, bugs/correctness, conventions). All were fixed before PR.
- WebSocket/STOMP integration and dual Kafka publish (STOMP + `TraderNotified`) worked end-to-end on first system test run.
- System test `NotificationTest` passed in 3m 28s with no flakiness.

## What Was Difficult

- **`web-application-type: none` conflict not anticipated:** The plan said to copy the test `application.yml` pattern from other services. Those services use `web-application-type: none`, which is incompatible with `@EnableWebSocketMessageBroker`. Discovered only at test runtime.
- **Missing test dependencies not in plan:** `mockito-kotlin` and `awaitility-kotlin` were required by the test patterns named in the plan but not listed in the build config slice. Discovered at compile time.
- **`KafkaTestUtils.getRecords` API mismatch:** Plan referenced the method without specifying the signature. Implementation used `Long` instead of `Duration`, causing a compile failure.
- **Bash convention violations:** Piped commands (`2>&1 | grep`, `| tail`) were used despite AGENTS.md conventions prohibiting them. Required user correction.
- **System test not run proactively:** Implementation was declared complete without running the system test. The user had to challenge the claim before verification was performed.
- **`libs.versions.toml` entry omitted:** `mockito-kotlin` was added with a hardcoded version string. The project convention (all versions via version catalog) was only enforced after the user asked explicitly.
- **`SystemTestBase` name collisions during review fix:** Adding helper methods to the base class caused compile failures due to name conflicts with private members in subclasses. Required reading subclasses before modifying.
- **Retrospective workflow hard to find:** The retrospective skill was not invoked proactively. The user had to request it explicitly.
- **Less-interaction conventions not followed:** AGENTS.md specifies conventions for reducing user interruptions. These were not consistently applied during implementation.
- **Retrospective not offered before PR:** The finishing-a-development-branch skill was about to be invoked without first offering a retrospective. The user had to request it.

## Suggested Improvements

### 1. AGENTS.md — `## Pre-Slice Checklist` section

**Description:** Bash conventions and project-wide rules (e.g. version catalog usage) were not re-consulted during implementation and drifted. A standing pre-slice checklist in AGENTS.md would make these rules active at each slice boundary rather than read-once-and-forgotten.

**Actionable Change:** Add a `## Pre-Slice Checklist` section to `AGENTS.md` listing must-check items before any slice begins, including:
- Re-read §Bash Conventions before any shell command
- After adding any dependency, verify its version is in `libs.versions.toml`
- Read any reference file named in the plan before implementing the slice
- Read subclasses/dependents before modifying a shared base class

Covers findings: F1, F4, F6, F2, F3, F7.

---

### 2. AGENTS.md — Binding-language rule for workflow trigger phrases

**Description:** Workflow trigger phrases (`workflow feature-impl`, `run feature-spec workflow for XY`) were not treated as binding commitments to complete every step in order. Steps later in the sequence (retrospective, PR gate) were dropped under context pressure.

**Actionable Change:** Add a paragraph to `AGENTS.md`:

> A trigger phrase such as `workflow feature-impl` or `run feature-spec workflow for XY` is a binding commitment to complete every step of the named workflow skill, in order, before claiming completion. No step may be skipped. The workflow is not complete until all steps — including retrospective and finishing steps — are done.

Covers findings: F8, F9, F10.

---

### 3. Workflow skills — TodoWrite at invocation + explicit exit gates

**Description:** Workflow skills are read once at invocation and then fade from context. Steps late in the sequence are the most vulnerable. Two structural changes make this mechanical:

1. Each workflow skill generates a `TodoWrite` for every step at the moment it is invoked, so completion is tracked persistently through the session.
2. Each step includes an explicit exit condition: *"You may not proceed to the next step until this step is verified complete."*

**Actionable Change:** Update `superpowers:executing-plans`, `superpowers:finishing-a-development-branch`, and any other workflow skill to:
- Open with a `TodoWrite` block creating one todo per step
- End each step description with: *"Required before next step."*

The retrospective step specifically should read: *"Required before offering finishing-a-development-branch options."*

Covers findings: F5, F8, F10.

---

### 4. Planning quality rule — Read reference files before naming them

**Description:** The plan named reference test files (e.g. `ExecutionKafkaListenerTest`, `KafkaTestUtils.getRecords`) without the planner reading them first. This caused missing dependencies and API mismatches to reach implementation.

**Actionable Change:** Add to the planning workflow guidance: *"Any file named as a reference pattern in the plan must be read by the planner before the plan is written. The planner is responsible for listing all imports and dependencies not already present in the build file."*

Covers findings: F2, F3.
