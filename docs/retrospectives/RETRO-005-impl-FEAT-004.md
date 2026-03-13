# RETRO-005: Feature Implementation — FEAT-004

Date: 2026-03-13
Workflow: feature-impl
Related: FEAT-004
Duration: ~1 session (branch setup → implementation review → retro)

---

## What Went Well

- **Pattern consistency was high.** The existing modules (risk, order) provided very clear precedent for every pattern needed: `@KafkaListener` with `ConsumerRecord<String, Any>` dispatch, `KafkaTemplate` fire-and-forget producers, `@DataJpaTest` fixtures, `@WebMvcTest` with `@MockitoBean`. No guesswork required.
- **TDD caught real gaps.** The implementation review found two missing intermediate-state saves (`RISK_APPROVED`, `EXECUTION_COMPLETE`). The tests already passed before the fix (final states were correct), but the review process against the acceptance criteria caught the deviation — exactly what the review step is for.
- **The Awaitility + orderId-filter pattern** for Kafka output assertions proved robust across all four integration test classes. Using a shared class-level consumer with orderId filtering prevented the `getSingleRecord` "more than one record" failure that appeared in the first Slice 2 run.
- **Slice isolation worked cleanly.** Each slice had a dedicated test class with its own `@EmbeddedKafka` declaration and `@DirtiesContext`. No cross-slice interference after the consumer pattern was established.

## What Was Difficult

- **Spec had an internal inconsistency in `SettlementRequested`.** The Data Model section listed `SettlementRequested(val trade: Trade, val order: Order)` while the Publisher table and plan showed only `(val trade: Trade)`. Required a clarification question before slice 1 could start. Cost: one extra round-trip.
- **`orderJson` gap was not visible in the spec.** The spec defined `SagaStateEntity` without any order storage, yet `ExecutionRequested(order)` requires the full `Order` on `RiskApproved`. This architectural incompleteness wasn't discovered until mid-Slice 2 — after Slice 1 had already been committed. Required backfilling the entity, updating the test fixture, and noting the deviation.
- **`ObjectMapper` temporal type deserialization.** The Slice 4 `TradeExecuted` test timed out because a manually constructed `ObjectMapper` lacked `JavaTimeModule`. `Trade.executedAt: Instant` serialized to ISO-8601 by Spring's context ObjectMapper but failed silent deserialization in the test's local mapper. Switching to `@Autowired ObjectMapper` fixed it immediately. The root cause was inconsistency between test and application mapper configuration.
- **JUnit 5 hash-based test ordering** caused `getSingleRecord` to fail in the first Slice 2 run: `OrderCancelled` test ran before `OrderPlaced` test (alphabetical hash), leaving an unconsumed `RiskCheckRequested` record. The fix (orderId-filtered Awaitility loop) is more resilient, but the initial failure required diagnosis.

## Suggested Improvements

### 1. Spec Clarity — Document `orderJson` storage requirement in entity specs

**Description:** When a saga orchestrator needs to republish event data received earlier (e.g., the `Order` in `ExecutionRequested`), the entity definition must include a storage strategy. The spec omitted this, creating a mid-implementation design decision and a committed slice that needed retrofitting.

**Actionable Change:** In `feature-spec.md` STEP 7 guidance, add: "If an orchestrator service must republish data from earlier events, the entity definition must explicitly specify how that data is stored (e.g., a JSON column). Do not leave this implicit."

### 2. Test Coverage — Establish `@Autowired ObjectMapper` as the standard for integration tests involving temporal types

**Description:** Tests that deserialize Kafka messages containing `Instant`, `LocalDate`, or `BigDecimal` fields will silently fail if they use a manually constructed `ObjectMapper` without the matching Spring Boot auto-configuration (JavaTimeModule, `WRITE_DATES_AS_TIMESTAMPS=false`). The failure mode is a timeout, not a clear error, making it hard to diagnose.

**Actionable Change:** Add a note to the integration test pattern in `docs/workflows/feature-impl.md` STEP 4b: "Inject `@Autowired lateinit var objectMapper: ObjectMapper` in integration tests that deserialize complex event types — never construct a standalone `ObjectMapper` unless the event type contains only primitive/UUID/String fields."

### 3. Spec Clarity — Flag multi-event topic payload signatures at spec time

**Description:** The `SettlementRequested` signature ambiguity (Data Model said `(trade, order)`, Publisher table said `(trade)`) required a clarification round-trip during implementation. Both referenced the same event but disagreed on its shape.

**Actionable Change:** In `feature-spec.md` STEP 7, add a consistency sub-check: "Verify that every event's field list is identical wherever it appears in the spec (Data Model, Publisher table, Consumer table). Mismatches must be resolved before the spec is committed."
