# RETRO-011: Feature Spec — FEAT-012

Date: 2026-03-17
Workflow: feature-spec
Related: FEAT-012
Duration: ~1 session (multi-machine, interrupted by connection issues)

---

## What Went Well

- **Root-cause analysis of `SagaCompensationTest` timeout** — correctly identified that
  `risk-service` and `execution-service` were missing from the Kafka consumer group readiness
  check in `SystemTestBase`, giving a precise, targeted fix rather than a generic "increase
  the timeout" approach.
- **Gradle deprecation analysis** — correctly distinguished between two different deprecation
  warnings: one plugin-internal and non-fixable (`StartParameter.isConfigurationCacheRequested`,
  Gradle 10.0 removal), and one actionable in our test code (`@MockBean`/`@SpyBean`, Spring
  Boot 3.4). Also correctly determined the "incompatible with Gradle 9.0" footer message is
  not a Gradle 9 blocker.

## What Was Difficult

- **Cloud environment mixed spec and implementation** — the agent running in the Claude cloud
  sandbox committed build file changes (Dockerfiles, `build.gradle.kts` files, `SystemTestBase`,
  CI workflows) in the same session as the spec docs, bypassing the spec approval gate entirely.
  This required manual cleanup and restarting the spec workflow on the correct machine.
- **GitHub push connection issues** — the cloud environment reported a successful push to a
  local git proxy (`127.0.0.1`) that did not reach the real GitHub remote. This caused confusion
  about branch state across machines and required re-doing the push from the correct environment.

## Suggested Improvements

### 1. Workflow Steps — Explicit implementation gate in feature-spec

**Description:** The feature-spec workflow has no explicit statement forbidding code changes.
The agent inferred it was allowed to implement because the spec was "done" and the changes were
obvious. There was no hard stop between spec approval and implementation start.

**Actionable Change:** Add a `> ⚠️ IMPLEMENTATION BOUNDARY` callout at the top of STEP 10 in
`docs/workflows/feature-spec.md` stating: "Only documentation files (`docs/`, `docs/arch/`,
`docs/registry.md`) may be staged in this commit. Any change to source files, build files, or CI
configuration is a workflow violation — stop and check with the user."

---

### 2. Documentation — Consolidate architecture.md into a coherent structure

**Description:** `docs/arch/architecture.md` has grown organically across many features. It now
mixes component maps, sequence diagrams, Kafka topic tables, Gradle layout notes, testing
conventions, and technology decisions without a clear top-level structure. New rules are appended
at the bottom, making the document hard to navigate and easy to miss during consistency checks.

**Actionable Change:** Dedicate a future session to restructuring `architecture.md` with explicit
top-level sections (e.g. `## System Components`, `## Messaging`, `## Persistence`, `## Testing
Conventions`, `## Build & Infrastructure`, `## Key Design Decisions`) and move all existing
content under the correct heading. No content should be added or removed — structure only.

---

### 3. Git Flow — Tighten cloud environment workflow

**Description:** When running in a cloud/sandbox environment, the agent pushes to a local git
proxy that may not be the real GitHub remote. The push appears to succeed locally but the branch
never reaches GitHub, causing silent divergence between environments.

**Actionable Change:** Add a note to `docs/workflows/git-for-agents.md` (or create it if absent)
stating that after any push, the agent must verify the remote URL with `git remote -v` and confirm
the branch is visible on the real remote before reporting success. If the remote URL is
`127.0.0.1` or `localhost`, the push has not reached GitHub.
