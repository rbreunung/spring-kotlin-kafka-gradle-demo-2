# RETRO-019: bug-fix — BUG-004

Date: 2026-03-29
Workflow: bug-fix
Related: BUG-004
Duration: ~30 minutes (brainstorming + implementation)

---

## What Went Well

- Brainstorming surfaced three genuinely distinct approaches (timeout increase, Kafka consumer disable, always-fail blocklist) before committing to a fix. This prevented the instinct to just inflate the existing `SETTLEMENT_ARTIFICIAL_DELAY_MS` value, which would have left the race intact.
- Option C (always-fail trader blocklist) targeted the actual root cause — the race exists because settlement can succeed — and removed it entirely rather than widening the window around it.
- The fix is fully additive: `always-fail-trader-ids` is an empty-default config property. Production is unaffected without any deployment-time discipline required.
- `SagaCompensationTest` shrank significantly: the `kafkaTemplate`, manual injection block, and early-skip for `SETTLED` were all deleted. The test is now straightforward — place order, await terminal state.

## What Was Difficult

- **Root cause was architectural, not a code defect:** The bug was not a missing null check or wrong type — it was a test design that relied on winning a timing race with a real service. That class of issue doesn't yield to standard debugging tactics; it required stepping back to ask "what structural change would make the race impossible?"
- **Option B (consumer disable) looked attractive but was fragile:** Disabling the Kafka consumer via Spring actuator requires the settlement service to expose and honour the actuator endpoint mid-test, adding a hidden coupling between the test and service internals. The brainstorming step surfaced this risk before any code was written.

## Suggested Improvements

### 1. Test Design — use deterministic config hooks instead of injection for compensation tests

**Description:** The original `SagaCompensationTest` tried to provoke a specific failure path by racing the real service. Any test that relies on injecting events to beat a running service to a state transition is inherently fragile. The always-fail blocklist pattern is a reusable alternative: add a config property to a service that makes it fail deterministically for a specific input, set that input in the test environment, and let the service fail on its own.

**Actionable Change:** When writing future compensation or error-path system tests, prefer a deterministic failure config hook over manual event injection. Document this pattern in `docs/arch/architecture.md` or a new ADR so it is available for the next service that needs a compensation test.

---

### 2. Workflow — run system tests locally before pushing a bug fix

**Description:** BUG-004 was discovered from a CI failure on PR #15, not from a local verification step before push. The `bug-fix` workflow ran `./gradlew :settlement:test` (unit tests only) but never exercised the system-test suite. Changes to `docker-compose.full.yml` or `SagaCompensationTest.kt` are invisible to unit tests — only `./gradlew :system-test:test` catches them.

**Actionable Change:** Add a verification step to `docs/workflows/bug-fix.md` after the unit-test pass:

> **STEP 6b — System test verification (if Docker is available):**
> Run `./gradlew :system-test:test`. If Docker is not running, either start it and re-run, or explicitly ask the user to run the system tests before merging.
>
> If any of the following were changed, this step is **mandatory, not optional**: `docker-compose.full.yml`, any `*SystemTest*.kt` or `*E2E*.kt` file, Kafka listener/producer wiring, or saga state transitions.

When running as an agent and Docker is not available, do not silently skip — instead surface the gap: *"System tests were not run locally because Docker is not available. Please run `./gradlew :system-test:test` before merging, or start Docker and I will run them."*

---

### 3. Process — brainstorm before fixing non-obvious concurrency bugs

**Description:** The first instinct when `SagaCompensationTest` failed was to increase the delay. That would have made CI slightly more stable without fixing the root cause, and the next slow runner would have broken it again. Spending a few minutes listing approaches (even informally) before writing code prevented that.

**Actionable Change:** For any bug where the symptom involves timing, intermittency, or concurrency, treat brainstorming as a required step rather than optional — specifically to ask: "Is there a fix that eliminates the race, or are we only widening the window?"
