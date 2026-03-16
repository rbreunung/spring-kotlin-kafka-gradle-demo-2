# RETRO-REVIEW-002: Spec Quality, Workflow Consistency, and Documentation Clarity

Date: 2026-03-16
Retrospectives Reviewed: RETRO-003, RETRO-004, RETRO-005, RETRO-006, RETRO-007, RETRO-008, RETRO-009
Previously Reviewed Up To: RETRO-REVIEW-001 (covered RETRO-001 and RETRO-002)

---

## Themes

### Theme 1: Spec Clarity

**Frequency:** 6 mentions (RETRO-003, RETRO-005, RETRO-006, RETRO-007, RETRO-008, RETRO-009)

**Examples:**
- RETRO-009: No step to enumerate concurrent-event scenarios — discovered reactively through user questions
- RETRO-005 + RETRO-003: Event field lists inconsistent across spec sections (Data Model vs. Publisher table)
- RETRO-003: Established patterns applied silently without confirming with user
- RETRO-007: Resilience4j bulkhead type not specified in spec

**Agreed Actions:**
1. Add STEP 6b (Concurrent Event Analysis) to `feature-spec` workflow — interactive discussion with user, produces guard coverage table, blocks progress until all scenarios are addressed or explicitly accepted
2. Add event field consistency check to `feature-spec` STEP 6 — verify field lists are identical across all spec sections; also check message topic/queue naming against architecture doc
3. Add cross-section consistency check and data-flow traceability check to `feature-impl` STEP 2 as a safety net for specs written before the new rule

---

### Theme 2: Workflow Steps

**Frequency:** 6 mentions (RETRO-003, RETRO-004, RETRO-005, RETRO-007, RETRO-008, RETRO-009)

**Examples:**
- RETRO-009: FEAT-011 added to registry as `draft` with no corresponding spec file
- RETRO-005: Spec completeness review before implementation missed detectable issues
- RETRO-005: Implementation decisions not systematically fed back to ADRs and architecture doc
- RETRO-007: Standalone `cd` tool calls triggered unnecessary approval prompts

**Agreed Actions:**
1. Add registry stub check to `feature-spec` STEP 10 — before committing, verify every new `draft` registry entry has a corresponding spec file; create stub if missing
2. Sync `feature-impl` skill with docs version — added Do-not-recreate-PLAN note (STEP 6) and ADR feedback loop (STEP 7) which were present in docs but missing from skill

---

### Theme 3: Test Coverage

**Frequency:** 4 mentions (RETRO-004, RETRO-005, RETRO-006, RETRO-008)

**Examples:**
- RETRO-005 + RETRO-006: Manual ObjectMapper construction caused silent deserialization failures in tests
- RETRO-004: New framework integrations not spiked before building on top of them
- RETRO-008: Settlement service had no health endpoint — readiness checks unreliable

**Agreed Actions:**
1. Add Testing Conventions section to `docs/arch/architecture.md` with project-specific rules (ObjectMapper injection, reference implementation)
2. Add generic pointer in `feature-impl` STEP 4b — "Before writing any test, check the Testing Conventions section in `docs/arch/architecture.md`"

---

### Theme 4: Documentation Clarity

**Frequency:** 4 mentions (RETRO-003, RETRO-005, RETRO-006, RETRO-009)

**Examples:**
- RETRO-005: Sequence diagrams required to make data flow gaps visible at spec time
- RETRO-009: Architecture diagrams use single color — hard to distinguish happy-path vs. compensation vs. failure flows
- RETRO-006: Known infrastructure constraints not documented in architecture doc

**Agreed Actions:**
1. Add Diagram Color Conventions section to `docs/arch/architecture.md` (blue = happy path, orange = compensation, grey = failure)
2. Retrofit existing sequence diagrams with `rect` phase groupings; add compensation path diagram
3. Update `feature-spec-template.md` Key Flows note to reference the color convention
4. Add `feature-spec` STEP 4 note to present infrastructure options before module/service design (also generalized technology-specific references throughout all workflow files)

---

### Not Actioned (Deferred)

| Item | Reason |
|---|---|
| RETRO-006: Add `logback-test.xml` to all modules | Implementation change — deferred to relevant FEAT |
| RETRO-008: Add actuator to settlement-service | Implementation change — deferred to FEAT-009 (Observability) |
| RETRO-004: Spike step for new framework integrations | Covered by existing TDD loop; too prescriptive for generic workflow |
| Point 6 color-coding: user review pending | User to review rendered diagrams and give feedback |

---

## Changes Made

| File | Change |
|---|---|
| `.claude/skills/feature-spec.md` | Add STEP 6b concurrent event analysis; event field + topic consistency check in STEP 6; registry stub check in STEP 10; generalize technology-specific references |
| `docs/workflows/feature-spec.md` | Mirror all skill changes |
| `.claude/skills/feature-impl.md` | Add data-flow traceability and cross-section checks to STEP 2; add Do-not-recreate-PLAN note to STEP 6; add ADR feedback loop to STEP 7; add Testing Conventions pointer to STEP 4b |
| `docs/workflows/feature-impl.md` | Mirror all skill changes |
| `.claude/skills/retro-review.md` | Add skill/docs sync reminder to STEP 5 |
| `docs/arch/architecture.md` | Add Diagram Color Conventions section; add Testing Conventions section; retrofit sequence diagrams with `rect` phase groupings; add compensation path diagram |
| `docs/templates/feature-spec-template.md` | Add color convention reference to Key Flows note |
