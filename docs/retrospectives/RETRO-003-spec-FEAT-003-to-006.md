# RETRO-003: Feature Spec Session — FEAT-003 to FEAT-006

Date: 2026-03-13
Workflow: feature-spec (combined)
Related: FEAT-003, FEAT-004, FEAT-005, FEAT-006
Duration: ~2 sessions (FEAT-003+004 in session 1; FEAT-005+006 in session 2)

---

## What Went Well

- **Brainstorming pace** — Q&A flow for design decisions (CB config, topic naming, position math, observability approach) felt well-paced. Each decision was explored before committing to the spec.
- **Incremental design reviews** — Presenting design section-by-section with approval gates caught issues in real time: the REST observability addition for SagaOrchestrator and the `SettlementRequested` event correction both surfaced during review rounds, not after writing.
- **Consistent spec format** — All 4 specs follow the same structure as FEAT-002 (goals, non-goals, architecture, data model, API surface, edge cases, configuration, acceptance criteria, file list). Templates were applied correctly and consistently.
- **Pattern consistency** — Resilience4j patterns (CB for Risk, Retry+Bulkhead for Settlement), Kafka topic naming conventions, and test patterns (embedded Kafka, sentinel `application.yml`) are coherent across all 4 specs.

## What Was Difficult

- **`SettlementRequested` event design gap** — Initial definition `SettlementRequested(val trade: Trade)` lacked the `traderId`, `symbol`, `quantity`, and `side` fields that SettlementService needs for position updates. The gap was discovered during FEAT-006 design rather than during FEAT-004, requiring a correction pass on both the spec and architecture doc.

- **Decisions presented without user confirmation** — For FEAT-005/006, some design choices (e.g. using `settlement-requests` topic, including `order: Order` in `SettlementRequested`) were applied by the agent as "obvious from consistency" without walking the user through the reasoning and asking for confirmation. The user would have preferred to be presented with the rationale and make the decision — even when the answer is clear from an established pattern, explicit confirmation gives agency and may surface disagreement early.

## Suggested Improvements

### 1. Spec Clarity — Explicit pattern confirmation

**Description:** When a design decision follows an established pattern from a prior feature, the agent currently applies it silently or states it as a conclusion rather than presenting it as a decision point. This removes the user from the design process even when the answer is "obvious."

**Actionable Change:** Add a rule to `docs/workflows/feature-spec.md` (step 4 — Architecture brainstorm) and the brainstorming skill: *"When a design choice is consistent with an established pattern from a prior spec, name the pattern and confirm with the user: 'Following the [pattern name] established in [FEAT-NNN], I'd use [X] here — consistent with that approach, does this look right?' Do not silently apply patterns without confirmation."*

---

### 2. Workflow Steps — Topic contract review between related features

**Description:** FEAT-004 introduced `ExecutionRequested` and `SettlementRequested` with incorrect topic assignments (`executions`/`settlements` instead of dedicated `execution-requests`/`settlement-requests`). This inconsistency with the `risk-checks`/`risk-results` pattern was only caught when speccing the consuming services in FEAT-005/006, requiring a correction pass.

**Actionable Change:** Add a consistency-check step to `docs/workflows/feature-spec.md` between step 6 (Consistency check — conflicts with existing specs?) and step 7 (Write spec): *"For features that introduce new Kafka topics or events: review the existing Kafka topics table in `docs/arch/architecture.md` and verify that all new topics follow the established command/result separation pattern (dedicated request topic per service). Explicitly confirm topic names with the user before writing the spec."*
