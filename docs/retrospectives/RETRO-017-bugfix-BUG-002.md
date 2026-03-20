# RETRO-017: bug-fix — BUG-002

Date: 2026-03-20
Workflow: bug-fix
Related: BUG-002
Duration: ~30 minutes

---

## What Went Well

- Agent analyzed the issue directly from the stack trace — root cause (`ByteArraySerializer` vs deserialized object) was identified immediately without additional investigation rounds.

## What Was Difficult

- **No test coverage for the service-failure DLQ path:** An existing `DeadLetterTopicTest` covered only the malformed-JSON deserialization path. The service-failure path (listener processes a valid message but `SettlementService` throws) had no test, allowing the misconfiguration to reach production undetected.
- **Missing logs from runtime:** The original exception that triggered the DLQ attempt was not visible in the provided logs. Only the downstream `ClassCastException` in the DLQ publisher was shown, making the upstream cause opaque.

## Suggested Improvements

### 1. Test Coverage

**Description:** The `DeadLetterTopicTest` covered deserialization failure but not service-failure. This left the DLQ producer serializer misconfiguration untested. The bug would have been caught at PR time if a service-failure DLQ test existed.

**Actionable Change:** In `docs/workflows/bug-fix.md`, Step 3 (Investigate), add: *"Before writing the reproduction test, check whether existing tests cover the failure path. If not, note the coverage gap as a finding alongside the bug."*

---

### 2. Workflow Steps — Structured error logging

**Description:** The settlement service logged no structured error when `SettlementService.settle()` threw, making the original exception invisible in production logs. Only the DLQ failure was surfaced.

**Actionable Change:** In `settlement/src/main/kotlin/.../settlement/kafka/SettlementKafkaListener.kt`, wrap the `settlementService.settle()` call in a try/catch that logs `ERROR` with `tradeId` and the exception before re-throwing, so the originating failure is always visible in logs.

---

### 3. Workflow Steps — Coverage check in bug-fix workflow

**Description:** The bug-fix workflow has no step that prompts the agent to verify whether the affected failure path has existing test coverage. A structured check would have surfaced the `DeadLetterTopicTest` gap as part of investigation, not as an afterthought.

**Actionable Change:** In `docs/workflows/bug-fix.md`, Step 3 (Investigate), add a bullet: *"Check existing tests for the affected component. Identify whether the failure path is covered. If not, note the gap explicitly in the bug report's `## Progress` section before writing the reproduction test."*
