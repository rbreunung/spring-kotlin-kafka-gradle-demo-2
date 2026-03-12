# RETRO-001: Feature Spec + Implementation — FEAT-001

Date: 2026-03-12
Workflow: feature-spec + feature-impl (combined)
Related: FEAT-001 — Create Gradle Multi-Module Structure
Duration: ~2 sessions

---

## What Went Well

- **Domain scenario selection** — the banking domain brainstorm (trade execution, payments, portfolio) produced a rich, realistic domain with clear learning goals; the final Trade Execution Platform choice was well-motivated
- **Q&A-driven design** — back-and-forth questions clarified the module structure, deployment model, and Kafka infrastructure decisions efficiently, with concrete previews helping the user compare options
- **Module layout decision** — arriving at service-per-module + `:shared` was unambiguous once the deployment model was agreed; the compile-time boundary rationale was clear
- **Docker/infra decisions** — the KRaft vs Zookeeper explanation and hybrid JVM-dev/Docker-demo decision were well received; the user understood the trade-offs quickly
- **Slice-by-slice execution** — each of the 5 implementation slices was independently testable and committed separately; regression was never an issue
- **Gradle multi-module build** — the build structure compiled cleanly on the first attempt; per-module `build.gradle.kts` with BOM management was straightforward
- **All tests green** — every `contextLoads` test (6 services) and all 4 `DomainModelTest` assertions passed without requiring test configuration changes

---

## What Was Difficult

- **Kafka Docker advertised.listeners config** — the first `docker-compose.yml` used `PLAINTEXT://0.0.0.0` as the advertised listener, which the `apache/kafka:3.9` image rejects with: `advertised.listeners cannot use the nonroutable meta-address 0.0.0.0`. The fix required splitting into three named listeners: `PLAINTEXT` (internal broker-to-broker), `PLAINTEXT_HOST` (external host clients), `CONTROLLER` (KRaft controller). This was an agent error requiring a second iteration with `docker compose down` + config fix before Kafka came up healthy.

- **Feature and plan docs not finalised** — after the implementation review was written and the registry updated to `complete`, the FEAT-001 feature doc remained at `Status: in-progress` with all goal/acceptance checkboxes unchecked, and PLAN-001 remained `Status: draft` with all slice statuses `[ ] todo`. This required a separate cleanup commit in the next session.

---

## Suggested Improvements

### 1. Documentation Finalisation

**Category:** Documentation

**Description:** The implementation review step (step 6 of feature-impl) verifies acceptance criteria and writes the review table to the plan doc, but it does not explicitly instruct the agent to update the feature doc status or tick the goal/acceptance checkboxes. As a result, those fields were left in their initial "draft" state despite the work being complete.

**Actionable Change:** Update `docs/workflows/feature-impl.md` step 6 to add: "Set `Status: complete` in both the feature doc and plan doc. Tick all `[ ]` goal and acceptance criteria checkboxes in the feature doc. Verify no unchecked items remain before committing."

---

### 2. Missing Retrospective Workflow Doc

**Category:** Workflow Steps

**Description:** `docs/workflows/retrospective.md` is referenced by feature-spec (step 12) and feature-impl (step 8) but does not exist. An agent following either workflow has no guidance on the 3-question format, naming convention, registry allocation, or how to offer the retro to the user. The retro was only run in this session because the user explicitly requested it — the agent could not have offered it proactively.

**Actionable Change:** Create `docs/workflows/retrospective.md` describing the full retro flow (offer → allocate → 3 questions with agent self-reporting → write doc → commit). Add a link from step 12 in `feature-spec.md`, step 8 in `feature-impl.md`, and step 12 in `bug-fix.md`.

---

### 3. Infrastructure Choices Earlier in Spec

**Category:** Spec Clarity

**Description:** Docker/Kafka infrastructure decisions (Compose, KRaft mode, dev-vs-full-Docker workflow) arose mid-discussion after module structure was mostly agreed, requiring the user to pause and learn about Confluent, Zookeeper, advertised listeners, and dev-loop trade-offs before being able to decide. Presenting a compact infra Q&A earlier in the spec phase (before service/module design) would front-load this context.

**Actionable Change:** Update `docs/workflows/feature-spec.md` step 4 (architecture brainstorm) to add: "If the feature involves infrastructure (Kafka, databases, Docker), present infra options as a structured Q&A *before* module/service design — users need the deployment model agreed before service boundaries make sense."
