# RETRO-013: Feature Spec — FEAT-009

Date: 2026-03-18
Workflow: feature-spec
Related: FEAT-009
Duration: ~1 session

---

## What Went Well

- Spec was already solid — existing draft had clear goals, config snippets, and acceptance criteria; little rework needed
- Gap detection worked — the missing web stack and port issue was caught before implementation started, not during
- Decisions were quick — Q&A on ports, web stack, and metrics was focused and reached conclusions fast
- Plan slices are clear — PLAN-009 slices are well-scoped and unambiguous

## What Was Difficult

- Port confusion — initial question about ports was misunderstood (Kafka ports vs HTTP server ports); required clarification before the real question landed
- Web stack gap not in spec — Kafka-only services lacking `spring-boot-starter-web` was not captured in the original draft; discovered only when exploring the build files
- Missing workflow steps — STEP 11 (merge decision) and STEP 12 (retrospective) were not run initially and had to be caught by a follow-up check
- Proposed merge instead of PR — workflow offered a direct merge option, but the project uses GitHub PRs for all integrations; this should not have been offered

## Suggested Improvements

### 1. Workflow Steps: Workflow completion check

**Description:** After committing the spec (STEP 10), the session ended without running STEP 11 and STEP 12. The omission was only caught when the user explicitly asked whether all steps were done.

**Actionable Change:** Add a checklist at the end of STEP 10 in `docs/workflows/feature-spec.md` listing all remaining steps (11, 12) as explicit reminders before the session closes.

### 2. Spec Clarity: Prompt for HTTP ports in architecture brainstorm

**Description:** STEP 4 (architecture brainstorm) did not surface the question of HTTP ports for Kafka-only services. The gap was only found by reading the build files during planning.

**Actionable Change:** Add a bullet to STEP 4 in `docs/workflows/feature-spec.md`: "For any new or existing service that will expose HTTP endpoints (REST or actuator), confirm it has a web starter (`spring-boot-starter-web` or `webflux`) and an explicit `server.port`."

### 3. Git Flow: Always use pull requests — no direct merges to main

**Description:** STEP 11 offered a direct merge option. The project is on GitHub and all integrations must go through pull requests, not local merges to main.

**Actionable Change:** Update STEP 11 in `docs/workflows/feature-spec.md` to remove the direct merge option. Replace it with: "Create a PR for the spec branch. Implementation continues on this branch or a new branch off the updated main after the PR merges."
