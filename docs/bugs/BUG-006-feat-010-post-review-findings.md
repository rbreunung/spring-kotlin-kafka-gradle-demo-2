# BUG-006: FEAT-010 Post-Review Findings — Missing @KafkaListener groupId and Narrow Catch in awaitSagaSettled

Status: open
Severity: medium
Date: 2026-03-29
Reporter: code review

## Progress

Phase: intake
Hypothesis: —
Last Updated: 2026-03-29

---

## Environment

- OS: any
- Runtime/Version: JVM 21
- Framework/App Version: Spring Boot 3.x / Spring Kafka
- Relevant Config: notification/src/main/resources/application.yml (spring.kafka.consumer.group-id: notification-service)

## Steps to Reproduce

**Issue 1 — Missing groupId:**
1. Inspect `notification/.../kafka/NotificationKafkaListener.kt`
2. Observe `@KafkaListener(topics = ["notifications"])` has no `groupId` attribute
3. Compare with any other `@KafkaListener` in the project (e.g., `SettlementKafkaListener`) — all declare `groupId` explicitly

**Issue 2 — Narrow catch in awaitSagaSettled:**
1. Start the system test stack with a slow or overloaded broker
2. Run `./gradlew :system-test:test`
3. If saga-orchestrator returns a 5xx during startup, `RestClientException` propagates from `awaitSagaSettled` and the test fails immediately instead of retrying

**Issue 3 — WebSocketConfigTest missing SimpMessagingTemplate assertion:**
1. Read `notification/.../config/WebSocketConfigTest.kt`
2. Observe that it only asserts `GET /ws/info` returns HTTP 200
3. Compare with FEAT-010 spec acceptance criterion: "WebSocket config test: verify configuration loads and `SimpMessagingTemplate` bean is available"

## Expected Behavior

**Issue 1:** `@KafkaListener` declares `groupId = "notification-service"` explicitly, consistent with all other listeners in the project.

**Issue 2:** `awaitSagaSettled` retries on any exception (5xx, connection refused, etc.) until the Awaitility timeout is reached.

**Issue 3:** `WebSocketConfigTest` explicitly asserts that the `SimpMessagingTemplate` bean is available in the application context.

## Actual Behavior

**Issue 1:** `@KafkaListener(topics = ["notifications"])` relies on `spring.kafka.consumer.group-id` from `application.yml` without declaring the group inline. Functional at runtime but breaks project convention and creates a silent coupling to config.

**Issue 2:** `awaitSagaSettled` catches only `HttpClientErrorException` (4xx). A 5xx response or `ResourceAccessException` causes the Awaitility condition to throw rather than return false, immediately failing the test.

**Issue 3:** `WebSocketConfigTest` probes the SockJS HTTP info endpoint (`/ws/info → 200`) but does not explicitly verify the `SimpMessagingTemplate` bean, which is what the spec acceptance criterion specifies.

## Affected Component(s)

- `notification/src/main/kotlin/de/antrophos/demo/spring/kafka/trader/notification/kafka/NotificationKafkaListener.kt` (Issue 1)
- `system-test/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/systemtest/SystemTestBase.kt` (Issue 2)
- `notification/src/test/kotlin/de/antrophos/demo/spring/kafka/trader/notification/config/WebSocketConfigTest.kt` (Issue 3)

## Severity

**medium** — No production correctness bug. Issues 1 and 2 affect convention alignment and CI test reliability; Issue 3 is a spec wording gap with implicit coverage from `NotificationApplicationTests.contextLoads`.

## Workaround

**Issue 1:** None needed — behavior is correct, only convention is violated.
**Issue 2:** Re-run the failing system test; it is non-deterministic.
**Issue 3:** None needed — `SimpMessagingTemplate` bean is implicitly verified by application context startup.

---

## Root Cause

**Issue 1:** `groupId` omitted from `@KafkaListener` annotation during implementation. The group-id resolves correctly from `application.yml` at runtime, masking the omission.

**Issue 2:** `awaitSagaSettled` was written to handle the expected 404-before-saga-exists case. The broader transient-error case (5xx, connection refused during startup) was not anticipated.

**Issue 3:** The spec acceptance criterion says "verify `SimpMessagingTemplate` bean is available". The test author chose to test the observable behavior (SockJS endpoint reachability) rather than the bean directly. Both approaches verify the config loaded, but only the bean assertion satisfies the literal spec wording.

## Fix Summary

> Agent: fill after fix is implemented.

**Issue 1 fix:**
- In `NotificationKafkaListener.kt:14`, change:
  ```kotlin
  @KafkaListener(topics = ["notifications"])
  ```
  to:
  ```kotlin
  @KafkaListener(topics = ["notifications"], groupId = "notification-service")
  ```

**Issue 2 fix:**
- In `SystemTestBase.kt`, change the catch block inside `awaitSagaSettled`:
  ```kotlin
  catch (e: HttpClientErrorException) { false }
  ```
  to:
  ```kotlin
  catch (_: Exception) { false }
  ```

**Issue 3 fix (optional):**
- In `WebSocketConfigTest.kt`, add an autowired `SimpMessagingTemplate` field with `assertNotNull` assertion, or add a comment documenting that implicit coverage via `contextLoads` is accepted in lieu of the literal spec criterion.

- **Test added:** n/a (fixes are one-line changes to production and test infrastructure; no new test methods required for Issues 1 and 2)
- **Commit:** —
