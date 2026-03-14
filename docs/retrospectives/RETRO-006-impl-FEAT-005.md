# RETRO-006: feature-impl — FEAT-005

Date: 2026-03-14
Workflow: feature-impl
Related: FEAT-005
Duration: ~1 session

---

## What Went Well

- Few unexpected interactions required from the user — the implementation was largely autonomous
- TDD loop stayed clean: each slice had a failing test before implementation, and tests drove the design forward
- Slice structure from PLAN-005 was well-suited to the implementation; no replanning required

## What Was Difficult

- **`JsonSerializer` / Jackson `Instant` incompatibility** — Spring Kafka 3.3.x with Jackson 2.19.x does not register `JavaTimeModule` by default. `Trade.executedAt: Instant` caused silent publish failures in the listener container (logged as "Record in retry and not yet recovered" with no prominent exception). Required creating a custom `KafkaConfig.kt` with a full `KafkaTemplate` bean. Took multiple test iterations to diagnose.
- **Kotlin null-safety breaks standard Mockito matchers** — `ArgumentCaptor.capture()` and `any()` return null at the call site, which Kotlin's intrinsic null checks reject before reaching the mock. Required abandoning `doAnswer`, raw `ArgumentCaptor`, and finally adding `mockito-kotlin:5.4.0` as an explicit dependency.
- **Test failure debugging required HTML/XML report parsing** — When `./gradlew :execution:test` failed, Gradle's output was too brief to show the actual exception. Diagnosing required multiple grep/python invocations on HTML test reports, which was slow and awkward. The user correctly noted this was the main source of unnecessary interaction.

## Suggested Improvements

### 1. Test Coverage

**Description:** When a test fails during implementation, the actual exception is buried in Gradle's HTML/XML test reports. Getting the failure message required multiple complex grep/python commands on report files, causing unnecessary back-and-forth.

**Actionable Change:** Add a `logback-test.xml` to `execution/src/test/resources/` (and establish this as a project-wide convention by noting it in `docs/arch/architecture.md` under "Testing Conventions") that writes test log output to `build/logs/test.log` at DEBUG level. When a test fails, a single `Read` of `build/logs/test.log` surfaces the full stack trace immediately.

### 2. Spec Clarity

**Description:** The spec did not note the `JsonSerializer` / `JavaTimeModule` incompatibility risk for events containing `java.time.Instant` fields. Any future service publishing `TradeExecuted` (or any event with `Instant`) would hit the same issue without knowing why.

**Actionable Change:** Add a note to `docs/arch/architecture.md` under a new "Known Infrastructure Constraints" section: "Spring Kafka's `JsonSerializer` does not register `JavaTimeModule` by default with Jackson 2.19.x. Any module that publishes Kafka events containing `java.time.Instant` fields must provide a custom `KafkaTemplate` bean with a properly configured `ObjectMapper`." Also reference `execution/src/main/kotlin/.../execution/KafkaConfig.kt` as the reference implementation.

### 3. Other

**Description:** Kotlin's null-safety intrinsics break standard Mockito argument matchers (`capture()`, `any()`) for non-null parameters. This is a universal issue for all Kotlin unit tests that mock Kafka or other void-returning components. `mockito-kotlin` was added as a one-off dependency but it should be standard across all service modules.

**Actionable Change:** Add `testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")` to the root `build.gradle.kts` in the `subprojects {}` block so all service modules inherit it automatically. Remove the per-module declaration from `execution/build.gradle.kts`.
