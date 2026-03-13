# RETRO-004: Feature Implementation — FEAT-003

Date: 2026-03-13
Workflow: feature-impl
Related: FEAT-003 — Risk Service — Kafka Integration and Resilience4j Circuit Breaker
Duration: ~1 session (multi-context, continued from previous)

---

## What Went Well

- **Circuit breaker root cause found.** The `@CircuitBreaker` annotation failure was fully
  diagnosed: `spring-cloud-starter-circuitbreaker-resilience4j` does not auto-configure
  `FallbackExecutor`, so `CircuitBreakerAspect` is never created. The programmatic approach
  (`CircuitBreakerRegistry.circuitBreaker("riskEngine").executeSupplier {}`) worked on the
  first attempt after the root cause was understood.

- **Final test coverage is solid.** 11 tests pass across 5 test classes
  (`RiskExternalClientTest`, `RiskServiceTest`, `RiskEventPublisherTest`,
  `RiskKafkaIntegrationTest`, `RiskCircuitBreakerIntegrationTest`). The CB integration test
  reliably verifies the open/closed transition using `minimumNumberOfCalls: 5`.

---

## What Was Difficult

### 1. `@CircuitBreaker` annotation silently does nothing
The plan specified annotation-based AOP. It compiled and ran without error but the CB never
recorded failures — exceptions propagated to `RiskService` instead of routing to fallbacks.
`spring-cloud-starter-circuitbreaker-resilience4j` does not auto-configure `FallbackExecutor`
(required by `CircuitBreakerAspect`). Without it, the annotation is a no-op.
Resolved by rewriting `RiskExternalClient` to use the programmatic API via `CircuitBreakerRegistry`.
Documented in `risk/AGENTS.md`.

### 2. Test YAML replaces main YAML — CB config missing in test context
After implementing programmatic CB, all 6 integration test calls still returned `"evaluation-failed"`.
The CB config in `src/main/resources/application.yml` included `minimumNumberOfCalls: 5`, but
`src/test/resources/application.yml` (a full standalone replacement, not a merge) did not include the
CB block. The CB ran with default `minimumNumberOfCalls: 100` and never opened on 5-6 calls.
Resolved by duplicating the full CB config block in the test YAML. Documented in `risk/AGENTS.md`.

### 3. `@Autowired ObjectMapper` not found in non-web context
`spring-boot-starter` (without web) does not auto-configure a Jackson `ObjectMapper` bean.
Resolved by direct instantiation: `private val objectMapper = ObjectMapper().registerKotlinModule()`.
Documented in `risk/AGENTS.md`.

### 4. Mockito `any()` returns null — NPE with Kotlin non-nullable types
`verify(publisher, never()).publishRejected(any(), any())` caused NPE because `Mockito.any()`
returns null, which Kotlin rejects for non-nullable parameters.
Resolved by using `verifyNoMoreInteractions(publisher)` and concrete values in verify calls.

### 5. `UnfinishedStubbingException` when creating `CallNotPermittedException`
`CallNotPermittedException.createCallNotPermittedException(cbMock)` called `cbMock.getName()`
internally during active `when().thenThrow(...)` argument evaluation.
Resolved by extracting exception creation to a `val` before the `when()` call, and using a real
`CircuitBreakerRegistry.ofDefaults().circuitBreaker("test")` instead of a mock.

### 6. `spring.main.web-application-type: none` silently dropped
`application.yml` was rewritten (adding Kafka + CB config) without reading the existing file first.
The `spring.main.web-application-type: none` property was lost. Required an extra fix commit.

### 7. Premature `RiskEventPublisher` dependency in stub (cascade from #1)
The plan designed CB with annotation-based fallback methods in `RiskExternalClient`, which needed
`RiskEventPublisher`. When the design pivoted to programmatic CB (with `RiskService` catching
exceptions), the stub still carried the now-unnecessary dependency. The mid-implementation design
change was not propagated to existing artifacts. Resolved by removing the premature dependency in a
separate commit.

### 8. Docs committed to wrong git context
Doc files were committed to the main repo branch using `git -C` while working in the feature
worktree. All commits — code and docs — belong on the work branch. When main has updates needed on
the feature branch, the correct flow is `git merge main` into the feature branch.

### 9. Subagents not following project conventions
Dispatched subagents performed unexpected git operations and did not follow project branch/workflow
conventions, requiring manual user intervention. The parent agent did not pass project context
(AGENTS.md, workflow docs) when dispatching subagents.

### 10. Ad-hoc diagnostic code deleted instead of converted to tests
A temporary `CbDiagTest.kt` was created to investigate the CB annotation failure, then deleted after
use. The investigative insight was not preserved as reusable test code. `RiskExternalClientTest.kt`
was added afterwards — it should have been the diagnostic directly.

### 11. Workflow interrupted for skills folder read permission
The agent attempted to read skill files directly from `~/.claude/plugins/` instead of using the
`Skill` tool, triggering an interactive permission prompt. The `Skill` tool exists specifically to
invoke skills without requiring direct file access.

### 12. Complex git commands caused interaction
The agent used `git -C /path/to/main/repo show main:docs/file.md` and other non-standard git
operations to read spec files from the main branch while in the feature worktree. These triggered
permission prompts and are fragile. The correct approach is to merge main into the feature branch
first, then read files normally.

### 13. JAR and zip file inspection
To diagnose which beans Spring auto-configures, the agent unpacked library JARs to inspect class
files. This triggered permission prompts, was slow, and unnecessary. A `@SpringBootTest` that
calls `ctx.beanDefinitionNames` gives the same information with zero permissions overhead.

### 14. HTML test report parsing via shell scripts
The agent attempted to parse Gradle HTML test reports with custom shell pipelines. The same
information is available as structured XML at `build/test-results/test/*.xml`, or more directly by
running the specific failing test with `./gradlew :module:test --tests "..." --info`.

### 15. Access to `/tmp` caused interaction
Writing diagnostic output to `/tmp` triggered permission prompts. Temp and debug output should use
a git-ignored directory within the workspace (e.g. `build/agent-debug/`).

### 16. Gradle build cache masked stale state
The final verification `./gradlew :risk:test` could pass due to cached task results. Final
verification steps should use `./gradlew :risk:clean :risk:test` to guarantee a fresh build.

### 17. Git commits started late
The first several implementation slices were completed and tested before any commit was made. This
increases risk of work loss and makes git history harder to follow. Commits should follow slice
boundaries, not accumulate at the end of the session.

### 18. Stale feature branch not rebased
When resuming work on a feature branch that had fallen behind main, complex git operations were
attempted instead of merging main into the feature branch. If the branch has not been pushed to
origin, a rebase or branch recreation is cleaner.

---

## Suggested Improvements

### 1. New framework integrations require a spike verification step in the plan

**Category:** Planning

**Description:** The `@CircuitBreaker` annotation failure and the missing `ObjectMapper` bean were
both framework assumptions that turned out wrong for this Spring Boot starter combination. No plan
step verified that the wiring worked before the integration test was built on top of it. JARs were
also unpacked to understand Spring auto-configuration — a `@SpringBootTest` achieves the same result
without permission overhead.

**Actionable Change:** Any plan task introducing a new framework integration (Resilience4j CB, retry,
bulkhead, or any library not previously used in the project) must include an explicit spike step:
write a minimal `@SpringBootTest` that verifies the wiring works (e.g. the expected bean is registered,
or the CB can record failures) before building on top of it. To inspect registered beans: add a test
that calls `ctx.beanDefinitionNames`. Reference the module's `AGENTS.md` for known gotchas before
designing the integration. Never unpack JARs to investigate Spring configuration.

---

### 2. Read-before-write for config files + verify test YAML coverage

**Category:** Process discipline

**Description:** `spring.main.web-application-type: none` was dropped when `application.yml` was
replaced without reading the existing file first. Additionally, `src/test/resources/application.yml`
was created without verifying which blocks from the main YAML must be duplicated (Spring Boot
replaces, not merges, the test YAML). Both are read-before-write failures.

**Actionable Change:** Any plan step that creates or modifies a config file must include a preceding
read step: read the current file, list all properties that must be preserved, and confirm the new
version includes them before committing. When creating a test YAML, explicitly list which blocks are
required for tests to work (e.g. CB config, `web-application-type`) and confirm they are present.

---

### 3. Agent git workflow document + commit discipline

**Category:** Workflow

**Description:** Multiple git issues occurred in a single session: reading files from another branch
with `git -C`/`git show`, docs committed to the wrong branch, complex git pipelines triggering
interaction, commits accumulated across multiple slices rather than per slice. These all stem from
the absence of a concise git ruleset visible to agents. Parent agents also did not pass project
workflow context to dispatched subagents.

**Actionable Change:** Create `docs/workflows/git-for-agents.md` with an explicit command whitelist,
prohibited patterns, branch rules, and commit discipline. Parent agents must pass this file's content
to any subagent before it starts work. Commit once per vertical slice on the feature branch. Spec and
planning docs are committed on the current branch when complete.
