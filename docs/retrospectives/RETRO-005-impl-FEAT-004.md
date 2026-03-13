# RETRO-005: Feature Implementation — FEAT-004

Date: 2026-03-13
Workflow: feature-impl
Related: FEAT-004
Duration: ~1 session (branch setup → implementation review → retro)

---

## What Went Well

- **PR creation was straightforward.** The branch was clean, all commits were well-scoped, and the PR body could be generated directly from the feature spec's acceptance criteria and implementation notes. No rebasing or cleanup was needed before opening.
- **Pattern consistency was high.** The existing modules (risk, order) provided very clear precedent for every pattern needed: `@KafkaListener` with `ConsumerRecord<String, Any>` dispatch, `KafkaTemplate` fire-and-forget producers, `@DataJpaTest` fixtures, `@WebMvcTest` with `@MockitoBean`. No guesswork required.
- **TDD caught real gaps.** The implementation review found two missing intermediate-state saves (`RISK_APPROVED`, `EXECUTION_COMPLETE`). The tests already passed before the fix (final states were correct), but the review process against the acceptance criteria caught the deviation — exactly what the review step is for. The need to persist all intermediate states is now codified in [ADR-001](../arch/adr/ADR-001-saga-state-as-recovery-anchor.md) as a constraint for all future saga features.
- **The Awaitility + orderId-filter pattern** for Kafka output assertions proved robust across all four integration test classes. Using a shared class-level consumer with orderId filtering prevented the `getSingleRecord` "more than one record" failure that appeared in the first Slice 2 run.
- **Slice isolation worked cleanly.** Each slice had a dedicated test class with its own `@EmbeddedKafka` declaration and `@DirtiesContext`. No cross-slice interference after the consumer pattern was established.

## What Was Difficult

- **Spec had an internal inconsistency in `SettlementRequested`.** The Data Model section listed `SettlementRequested(val trade: Trade, val order: Order)` while the Publisher table and plan showed only `(val trade: Trade)`. Required a clarification question before slice 1 could start. Cost: one extra round-trip.
- **`orderJson` gap was not visible in the spec.** The spec defined `SagaStateEntity` without any order storage, yet `ExecutionRequested(order)` requires the full `Order` on `RiskApproved`. This architectural incompleteness wasn't discovered until mid-Slice 2 — after Slice 1 had already been committed. Required backfilling the entity, updating the test fixture, and noting the deviation.
- **`ObjectMapper` temporal type deserialization.** The Slice 4 `TradeExecuted` test timed out because a manually constructed `ObjectMapper` lacked `JavaTimeModule`. `Trade.executedAt: Instant` serialized to ISO-8601 by Spring's context ObjectMapper but failed silent deserialization in the test's local mapper. Switching to `@Autowired ObjectMapper` fixed it immediately. The root cause was inconsistency between test and application mapper configuration.
- **JUnit 5 hash-based test ordering** caused `getSingleRecord` to fail in the first Slice 2 run: `OrderCancelled` test ran before `OrderPlaced` test (alphabetical hash), leaving an unconsumed `RiskCheckRequested` record. The fix (orderId-filtered Awaitility loop) is more resilient, but the initial failure required diagnosis.

## Suggested Improvements

### 1. Spec Clarity — Document `orderJson` storage requirement in entity specs; require sequence diagram

**Description:** When a saga orchestrator needs to republish event data received earlier (e.g., the `Order` in `ExecutionRequested`), the entity definition must include a storage strategy. The spec omitted this, creating a mid-implementation design decision and a committed slice that needed retrofitting. A sequence diagram showing the full data flow would have made this gap visible at spec time: the arrow `SagaOrchestrator -->> ExecutionService: ExecutionRequested(order)` immediately raises the question "where does the orchestrator get `order` at this point?"

**Actionable Changes:**
1. In `feature-spec.md` STEP 7 guidance, add: "If an orchestrator service must republish data from earlier events, the entity definition must explicitly specify how that data is stored (e.g., a JSON column). Do not leave this implicit."
2. The `feature-spec-template.md` Architecture section now requires a Mermaid `sequenceDiagram` for any feature involving inter-service communication or multi-step data flow. This has been added to `docs/templates/feature-spec-template.md`.
3. A full order lifecycle sequence diagram has been added to `docs/arch/architecture.md` as the canonical reference for the happy path.

### 2. Test Coverage — Establish `@Autowired ObjectMapper` as the standard for integration tests involving temporal types

**Description:** Tests that deserialize Kafka messages containing `Instant`, `LocalDate`, or `BigDecimal` fields will silently fail if they use a manually constructed `ObjectMapper` without the matching Spring Boot auto-configuration (JavaTimeModule, `WRITE_DATES_AS_TIMESTAMPS=false`). The failure mode is a timeout, not a clear error, making it hard to diagnose.

**Actionable Change:** Add a note to the integration test pattern in `docs/workflows/feature-impl.md` STEP 4b: "Inject `@Autowired lateinit var objectMapper: ObjectMapper` in integration tests that deserialize complex event types — never construct a standalone `ObjectMapper` unless the event type contains only primitive/UUID/String fields."

### 3. Spec Clarity — Flag multi-event topic payload signatures at spec time

**Description:** The `SettlementRequested` signature ambiguity (Data Model said `(trade, order)`, Publisher table said `(trade)`) required a clarification round-trip during implementation. Both referenced the same event but disagreed on its shape.

**Actionable Change:** In `feature-spec.md` STEP 7, add a consistency sub-check: "Verify that every event's field list is identical wherever it appears in the spec (Data Model, Publisher table, Consumer table). Mismatches must be resolved before the spec is committed."

### 4. Process Gap — Spec completeness review did not surface known open points

**Description (raised by user):** Before implementation began, a spec completeness review was performed as part of the feature-impl workflow. It did not surface the two main gaps that caused friction during implementation: the `orderJson` storage omission and the `SettlementRequested` signature inconsistency. Both were present in the committed spec and were findable by inspection — yet the review step missed them. This is a process reliability concern: if the completeness check does not catch detectable issues, it provides false confidence.

**Actionable Change:** In `feature-impl.md` STEP 2 (spec review), add explicit checklist items:
- For every event published by the feature, verify that the publishing component has access to all required fields at the time of publishing — trace back through the data flow and confirm each field is available (received earlier, stored in entity, or derivable).
- Cross-check every event's field list across all spec sections (Data Model, Publisher table, Consumer table, sequence diagram). Any mismatch is a blocker before implementation starts.

### 5. Process Gap — Implementation learnings not systematically fed back to architecture docs and ADRs

**Description (raised by user):** Decisions made during implementation (e.g., the `orderJson` field, the `SettlementRequested` Option A resolution, the intermediate-state persistence rule) were captured in the feature doc's Implementation Notes and in ADR-001. However, there is no systematic step in the workflow that ensures these learnings reach `architecture.md` or the ADR index in a way that future spec sessions will find and use them for consistency checks. If a future feature spec is written without reading ADR-001, the intermediate-state rule will be silently violated again.

**Actionable Change:** In `feature-impl.md` STEP 7 (retrospective / wrap-up), add: "Review all implementation deviations and decisions. For each one, determine whether it represents an architectural rule or constraint applicable to future features. If yes: (a) create or update an ADR, (b) ensure `architecture.md` references the ADR in its Key Design Decisions table, (c) add a pointer in the relevant feature-spec checklist or workflow step so future spec authors are directed to it." ADR-001 is the first instance of this pattern for this project.
