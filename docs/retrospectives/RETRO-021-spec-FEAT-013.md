# RETRO-021: feature-spec — FEAT-013

Date: 2026-03-29
Workflow: feature-spec
Related: FEAT-013
Duration: ~45 minutes (brainstorming across two sessions + spec write)

---

## What Went Well

- **Design-first brainstorming paid off.** The brainstorming session (spread across two context windows) produced a fully agreed design before the spec workflow started. The spec session itself was fast because all design questions were already resolved — no backtracking during Q&A steps 4 and 5.
- **ADR-003 identification was natural.** The Dockerfile pattern decision surfaced immediately as binding for all future services. The spec workflow's STEP 9 trigger ("significant architectural decision that constrains future work") fit the situation exactly — no ambiguity about whether an ADR was warranted.
- **Rate limit analysis produced a better design.** The question about Docker Hub and Maven rate limits (raised between the brainstorming and spec sessions) led to splitting `dockerVolumeClean` from `dockerImageClean` and adding the CI pre-pull + optional login steps. Without that analysis, the design would have addressed build speed but not reliability.

## What Was Difficult

- **`Skill` tool failed for `feature-spec`.** The skill invocation returned an error; the skill file had to be read directly with the `Read` tool. This added a small friction step at the start and required manual adherence to the workflow rather than letting the skill guide it.
- **Cross-session context loss.** The brainstorming work happened in a prior context window. Reconnecting required reading the plan file, task list, and prior design decisions — possible, but slower than a continuous session. The agreed design was reconstructed accurately, but some nuance (e.g., the exact `systemTest` Kotlin DSL form) had to be re-reasoned rather than recalled.
- **Gradle task DSL uncertainty.** The `systemTest` task's ordering guarantee (bootJar before `:system-test:test`) requires both `dependsOn` and `mustRunAfter`. The exact Kotlin DSL that compiles cleanly and behaves correctly could not be verified at spec time — it was left as an open question in the plan. Infrastructure/tooling features that involve Gradle DSL have a class of errors that only surface at build time.

## Suggested Improvements

### 1. Workflow — README as a mandatory checklist item in feature-spec

**Description:** The README was not in the spec's goals or plan slices until the user explicitly flagged it after the spec was written and committed. The "Full Docker Workflow" section would have contained incorrect instructions after FEAT-013 landed (still saying `--build` builds JARs from source). For any feature that changes how users run the project, README updates are not optional — but the current feature-spec workflow has no step that prompts for it.

**Actionable Change:** Add a README review prompt to STEP 5 (Scope Clarification) in `docs/workflows/feature-spec.md`: *"Does this feature change how the user runs the project, starts services, or runs tests? If yes, add a README update to goals and acceptance criteria."* Add a corresponding reminder to STEP 10 (Write Implementation Plan): *"If goals include a README update, add a dedicated slice for it."*

---

### 2. Workflow — make ADR criteria explicit in feature-spec STEP 9

**Description:** STEP 9 currently says *"If this feature introduces a significant architectural decision (a choice that will constrain future work)"* — which is correct but relies on judgment. During this session the criteria was obvious (Dockerfile pattern is binding for all future services), but for less clear-cut cases this vague threshold could cause an ADR to be skipped or written unnecessarily. Making the criteria concrete reduces ambiguity and keeps ADRs valuable.

**Actionable Change:** Add a concrete checklist to STEP 9 in `docs/workflows/feature-spec.md`: write an ADR if any of the following apply — (1) the decision sets a pattern all future services or modules must follow; (2) the decision explicitly rejects a previously considered alternative that may resurface; (3) the decision constrains how a technology or framework is used project-wide. If none apply, no ADR is needed.

---

### 3. Workflow — add a "Gradle DSL verify" sub-step for infrastructure slices in feature-impl

**Description:** The `systemTest` task ordering (open question in PLAN-013) is a known gap that can only be resolved by running the build. Infrastructure and tooling features involving Gradle Kotlin DSL have this characteristic — the spec can describe intent but cannot verify correctness. Leaving it as an open question in the plan is the right call, but the feature-impl workflow has no step that flags this type of slice as "must verify compiles before proceeding to next slice."

**Actionable Change:** Add a note to `docs/workflows/feature-impl.md` (or the impl plan template): *"For slices involving Gradle build script changes, verify `./gradlew tasks` compiles successfully before marking the slice done."* This makes the verify step explicit rather than implicit.
