# RETRO-018: bug-fix — BUG-003

Date: 2026-03-29
Workflow: bug-fix
Related: BUG-003
Duration: ~45 minutes

---

## What Went Well

- Adding structured error logging to `SettlementKafkaListener` as the first diagnostic step proved effective: the exception was captured immediately on the first test run and pointed directly to the root cause.
- The reproduction test confirmed the bug reliably (order 1 passes, order 2 produces `SettlementFailed`) and became a clean regression test after the fix.
- The fix was a single-line addition to `build.gradle.kts` — the smallest possible change, matching an existing pattern already in use in `saga-orchestrator`.

## What Was Difficult

- **Initial hypothesis was wrong:** Static analysis suggested a JPA detached-entity conflict (`existing.copy()` + `em.merge()`). The actual exception was `InstantiationException: No default constructor for entity`, which is a different JPA pitfall entirely. The static analysis phase consumed time on a false lead before the reproduction test revealed the real exception.
- **`@DataJpaTest` masked the bug:** `PositionPersistenceTest` already had a test calling `updatePosition()` twice for the same trader/symbol — and it passed. This is because `@DataJpaTest` wraps the test in a single transaction, causing Hibernate to serve `findByTraderIdAndSymbol()` from the first-level cache (no `SELECT` issued, no no-arg constructor needed). The bug only surfaced under `@SpringBootTest` where each repository call runs in its own transaction and Hibernate executes a real `SELECT`. This discrepancy between test scopes silently allowed a broken configuration to ship.

## Suggested Improvements

### 1. Test Coverage — `@DataJpaTest` vs `@SpringBootTest` for JPA entities

**Description:** `@DataJpaTest` tests for `PositionEntity` can pass even when the entity is misconfigured for Hibernate, because the wrapping transaction keeps entities in the first-level cache and avoids the `SELECT`/instantiation path. The gap between `@DataJpaTest` and `@SpringBootTest` behaviour means JPA configuration bugs can be invisible to unit tests.

**Actionable Change:** In `docs/features/FEAT-006-settlement-service.md` (or the corresponding implementation checklist), add a note: *"At least one `@SpringBootTest` integration test must exercise the `findByTraderIdAndSymbol()` → `applyTrade()` path (two sequential orders for the same trader/symbol) to catch Hibernate instantiation issues that `@DataJpaTest` would mask."*

---

### 2. Documentation — `kotlin("plugin.jpa")` requirement not documented

**Description:** The bug existed because the `settlement` module was missing `kotlin("plugin.jpa")`, but nothing in the codebase or docs warned that this plugin is required when using a Kotlin `data class` as a JPA `@Entity`. Another developer adding a new JPA module would likely repeat the same mistake.

**Actionable Change:** In `docs/arch/architecture.md` (or a new ADR), add a standing note: *"All modules that define `@Entity` classes as Kotlin `data class` must apply `kotlin(\"plugin.jpa\")` in their `build.gradle.kts`. Without it, Hibernate cannot instantiate the entity from a `SELECT` result set. See BUG-003."*
