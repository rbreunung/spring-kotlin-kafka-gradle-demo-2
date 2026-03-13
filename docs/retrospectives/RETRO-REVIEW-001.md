# RETRO-REVIEW-001: First Retro Review

Status: complete
Date: 2026-03-13

## Retrospectives Covered

- [RETRO-001](RETRO-001-feat-001-spec-and-impl.md) — FEAT-001 spec and implementation
- [RETRO-002](RETRO-002-feat-002-feature-impl.md) — FEAT-002 feature-impl

## Theme Analysis

### Already Applied — No Action Needed

| Retro | Suggestion | Evidence |
|---|---|---|
| RETRO-001 #1 | Feature + plan docs not finalised at end of feature-impl | `feature-impl.md` step 6 already instructs to set `Status: complete` and tick checkboxes |
| RETRO-001 #2 | Missing `retrospective.md` workflow doc | `docs/workflows/retrospective.md` exists with full retro flow |
| RETRO-001 #3 | Infrastructure Q&A should come before module/service design | `feature-spec.md` step 4 already has this instruction |
| RETRO-002 #2 | Bash command approval friction | `AGENTS.md` "Bash Command Style" section added; `settings.local.json` cleaned |

### Applied in This Review

| Retro | Suggestion | Change |
|---|---|---|
| RETRO-002 #1 | Plan doc overwritten during implementation cleanup | Added "Do not recreate PLAN-NNN" note to `feature-impl.md` step 6 |
| RETRO-002 #3 | Kafka test `application.yml` not in spec template | Added optional `## Configuration` section to `feature-spec-template.md` |

### Document-Level Gaps Fixed

| Document | Gap | Fix |
|---|---|---|
| `docs/features/FEAT-002-order-service.md` | Test `application.yml` absent from Configuration section | Added `order/src/test/resources/application.yml` subsection with full config and explanatory note |
| `docs/plans/PLAN-002-order-service.md` | No `## Implementation Review` table (PLAN-001 had one) | Added populated table covering all 16 acceptance criteria — all passed |

## Changes Made

| File | Change |
|---|---|
| `docs/workflows/feature-impl.md` | Added "Do not recreate PLAN-NNN" note to step 6 |
| `docs/templates/feature-spec-template.md` | Added optional `## Configuration` section after Edge Cases |
| `docs/features/FEAT-002-order-service.md` | Added test `application.yml` subsection to Configuration |
| `docs/plans/PLAN-002-order-service.md` | Added `## Implementation Review` table with all 16 criteria |
| `docs/registry.md` | Allocated RETRO-REVIEW-001 |
| `docs/retrospectives/RETRO-REVIEW-001.md` | Created this file |
