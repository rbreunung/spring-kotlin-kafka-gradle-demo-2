# RETRO-REVIEW-003: 2026-03-20

Date: 2026-03-20
Retrospectives Reviewed: RETRO-010, RETRO-011, RETRO-012, RETRO-013, RETRO-014, RETRO-015, RETRO-016, RETRO-017
Previously Reviewed Up To: RETRO-REVIEW-002 (covered RETRO-003 through RETRO-009)

---

## Themes

### Theme 1: Workflow Step Skipping

**Frequency:** 3 mentions across 3 retrospectives (RETRO-013, RETRO-015, RETRO-016)

**Examples:**
- RETRO-013: Feature-spec steps 11 and 12 were not run; workflow ended after the commit
- RETRO-015: Less-interaction conventions not followed during implementation
- RETRO-016: Retrospective not offered before PR; workflow ended without completing finishing steps

**Agreed Actions:**
1. Add session checklist to `feature-impl` STEP 1 explicitly naming STEP 8 and STEP 9 as required
2. Add exit gate to `feature-impl` STEP 8: always offer retrospective before opening PR

---

### Theme 2: Convention Drift

**Frequency:** 2 mentions across 2 retrospectives (RETRO-015, RETRO-016)

**Examples:**
- RETRO-016: Bash piped commands used against project convention; hardcoded versions in `build.gradle.kts` instead of `libs.versions.toml`
- RETRO-015: Tool call descriptions were generic ("shell command") rather than intent-based

**Agreed Actions:**
1. Add Pre-Slice Checklist to `AGENTS.md` requiring re-read of conventions, version catalog check, and parallel batching discipline
2. Add binding-workflow rule to `AGENTS.md`: invoking a workflow commits you to completing all its steps

---

### Theme 3: Bug Fix Workflow Gaps

**Frequency:** 2 mentions across 2 retrospectives (RETRO-014, RETRO-017)

**Examples:**
- RETRO-014: Investigation began before BUG-NNN doc was created and registered
- RETRO-017: No coverage check for affected failure path before proposing fix; DLQ path had no existing tests

**Agreed Actions:**
1. Add compliance gate to bug-fix STEP 1: BUG-NNN doc must exist before source files are read
2. Add coverage check to bug-fix STEP 3: identify existing tests for affected failure path before proposing fix

---

### Theme 4: Workflow Boundary Violations

**Frequency:** 2 mentions across 2 retrospectives (RETRO-011, RETRO-013)

**Examples:**
- RETRO-011: Spec session made source code changes without transitioning to feature-impl workflow
- RETRO-013: Agent used direct merge to main instead of creating a GitHub pull request

**Agreed Actions:**
1. Add boundary callout to `feature-spec` header: spec sessions produce documentation only, no source code changes
2. Add PR-only enforcement note to `feature-spec` STEP 11: always use a GitHub PR, never merge directly to main

---

### Theme 5: Subagent Behavior / Tool Call Quality

**Frequency:** 2 mentions across 2 retrospectives (RETRO-015, RETRO-016)

**Examples:**
- RETRO-015: Independent file reads done sequentially instead of batched; high tool call volume
- RETRO-016: Subagents not following project conventions (Bash, version catalog)

**Agreed Actions:**
1. Add parallel batching rule to `AGENTS.md` Pre-Slice Checklist: batch independent reads into a single parallel tool call
2. Add intent-description rule to `AGENTS.md` Pre-Slice Checklist: every tool call must describe its intent, not just what it runs

---

## Changes Made

| File | Change Description |
|---|---|
| `.claude/skills/feature-spec.md` | Added boundary callout (no source code changes), added missing "Update registry status" paragraph to STEP 10, added PR-only enforcement to STEP 11 |
| `docs/workflows/feature-spec.md` | Added boundary callout, added PR-only enforcement to STEP 11 |
| `AGENTS.md` | Added Pre-Slice Checklist section (conventions re-read, version catalog, parallel batching, intent descriptions) and binding-workflow rule |
| `.claude/skills/bug-fix.md` | Added compliance gate to STEP 1, added coverage check to STEP 3 |
| `docs/workflows/bug-fix.md` | Added compliance gate to STEP 1, added coverage check to STEP 3 |
| `.claude/skills/feature-impl.md` | Added session checklist to STEP 1, added exit gate to STEP 8 |
| `docs/workflows/feature-impl.md` | Added session checklist to STEP 1, added exit gate to STEP 8 |
| `docs/registry.md` | Added RETRO-REVIEW-003 row |
