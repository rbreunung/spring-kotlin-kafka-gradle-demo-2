# PLAN-013: CI and Docker Build Reliability — Implementation Plan

Status: draft
Date: 2026-03-29
Feature: [FEAT-013](../features/FEAT-013-ci-docker-build-reliability.md)

## Progress

Current Slice: 0
Completed Slices: []
Last Updated: 2026-03-29

## Implementation Review

Status: pending
Reviewed: —

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `./gradlew systemTest` builds bootJars then runs system tests | CI system-test.yml pass | pending |
| `./gradlew unitTest` excludes `:system-test` module | Manual: verify no system-test task executed | pending |
| All 6 Dockerfiles contain no `./gradlew` invocations | Code review / grep | pending |
| `docker compose build` succeeds after `./gradlew bootJar` for all services | Manual: run both commands locally | pending |
| CI `system-test.yml` uses `./gradlew systemTest` and passes on GitHub Actions | GitHub Actions run | pending |
| `./gradlew dockerVolumeClean` removes volumes without removing images | Manual: verify `docker images` unchanged | pending |
| Docker Hub login step skips silently when secrets absent | CI run without secrets | pending |
| README "Running Tests" documents `unitTest` and `systemTest` with Docker prerequisite | Read-through review | pending |
| README "Full Docker Workflow" shows `./gradlew bootJar` prerequisite before `--build` | Read-through review | pending |

---

## Open Questions

- [ ] **`systemTest` task ordering:** Gradle's `dependsOn` does not guarantee execution order between sibling tasks. The recommended approach is to declare `dependsOn` on all `bootJar` tasks plus `:system-test:test`, and add `mustRunAfter` on `:system-test:test` for each `bootJar` task. Verify this compiles and behaves correctly in Kotlin DSL before closing this question.

---

## Vertical Slices

### Slice 1: Simplify all 6 service Dockerfiles

**What it delivers:** Each service Dockerfile becomes a single-stage copy-and-run; the JDK build stage is removed. This is the foundational change that enables all subsequent improvements.

**Files to touch:**
- `order/Dockerfile` — replace two-stage build with `FROM eclipse-temurin:21-jre-alpine` + `COPY order/build/libs/*.jar app.jar`
- `risk/Dockerfile` — same pattern, port 8081
- `execution/Dockerfile` — same pattern, port 8082
- `settlement/Dockerfile` — same pattern, port 8083
- `saga-orchestrator/Dockerfile` — same pattern, port 8085
- `notification/Dockerfile` — same pattern, port 8090

**Test description:** After running `./gradlew bootJar` for all services (or just `:order:bootJar` to verify the pattern), run `docker build -f order/Dockerfile .` from the repo root and confirm it succeeds. Confirm the resulting image contains only the JRE layer and the JAR — no Gradle files, no JDK.

**Status:** [ ] todo

---

### Slice 2: Add Gradle task aliases in root `build.gradle.kts`

**What it delivers:** Four new tasks accessible from the repo root: `unitTest`, `systemTest`, `dockerVolumeClean`, `dockerImageClean`. `systemTest` enforces bootJar ordering before `:system-test:test`.

**Files to touch:**
- `build.gradle.kts` (root) — add the following 4 tasks:

```kotlin
tasks.register("unitTest") {
    description = "Run all unit tests, excluding system tests"
    group = "verification"
    dependsOn(subprojects
        .filter { it.name != "system-test" }
        .mapNotNull { it.tasks.findByName("test") })
}

tasks.register("systemTest") {
    description = "Build all service JARs then run system tests (see ADR-003)"
    group = "verification"
    dependsOn(
        ":order:bootJar", ":risk:bootJar", ":execution:bootJar",
        ":settlement:bootJar", ":saga-orchestrator:bootJar", ":notification:bootJar",
        ":system-test:test"
    )
}

project(":system-test").tasks.named("test") {
    mustRunAfter(
        ":order:bootJar", ":risk:bootJar", ":execution:bootJar",
        ":settlement:bootJar", ":saga-orchestrator:bootJar", ":notification:bootJar"
    )
}

tasks.register<Exec>("dockerVolumeClean") {
    description = "Remove Docker data volumes only — preserves pulled images to avoid rate limit re-downloads"
    group = "docker"
    commandLine("docker", "volume", "prune", "-f")
}

tasks.register<Exec>("dockerImageClean") {
    description = "Remove unused Docker images — use sparingly to avoid Docker Hub rate limits"
    group = "docker"
    commandLine("docker", "system", "prune", "-f")
}
```

**Note on `unitTest` task:** `subprojects.filter { it.name != "system-test" }.mapNotNull { it.tasks.findByName("test") }` may require the subproject `test` tasks to be realized before the root project configures. If this causes a project evaluation ordering issue, an alternative is:
```kotlin
dependsOn(subprojects
    .filter { it.name != "system-test" }
    .map { "${it.path}:test" })
```
Verify which form compiles cleanly during implementation.

**Test description:** Run `./gradlew tasks --group verification` — confirm `unitTest` and `systemTest` appear. Run `./gradlew unitTest` — verify `:system-test:test` is NOT in the task execution log. Run `./gradlew systemTest` (after building JARs in Slice 1) — verify all 6 bootJar tasks run before `:system-test:test`.

**Status:** [ ] todo

---

### Slice 3: Update CI `system-test.yml`

**What it delivers:** GitHub Actions system-test workflow uses `./gradlew systemTest` (which builds JARs before Docker), adds a Docker Hub pre-pull step, and adds an optional Docker Hub login step guarded by secrets.

**Files to touch:**
- `.github/workflows/system-test.yml` — three changes:

1. Add Docker Hub login step (before pre-pull):
```yaml
- name: Log in to Docker Hub
  if: ${{ secrets.DOCKERHUB_USERNAME != '' }}
  uses: docker/login-action@v3
  with:
    username: ${{ secrets.DOCKERHUB_USERNAME }}
    password: ${{ secrets.DOCKERHUB_TOKEN }}
```

2. Add pre-pull step:
```yaml
- name: Pre-pull base images
  run: |
    docker pull eclipse-temurin:21-jre-alpine
    docker pull apache/kafka:3.9.0
    docker pull openzipkin/zipkin:3
```

3. Change test step:
```yaml
- name: Run system tests
  run: ./gradlew systemTest    # was: ./gradlew :system-test:test
  env:
    DOCKER_BUILDKIT: 1
```

**Test description:** Push the branch and verify the GitHub Actions `System Tests` workflow passes end-to-end. Confirm in the run log that:
- Pre-pull step executes and all 3 images are pulled (or report "already present" on warm cache)
- Gradle build log shows `bootJar` tasks completing before `:system-test:test` starts
- All system test classes pass (including `ConcurrentOrdersTest` and `SagaCompensationTest`)

**Status:** [ ] todo

---

### Slice 4: Update README

**What it delivers:** `README.md` accurately describes how to run tests and how to use the full Docker workflow after the Dockerfile pattern change. Without this, a user following the README after FEAT-013 would either run the wrong test command or try `docker compose up --build` without pre-built JARs and get a COPY error.

**Files to touch:**
- `README.md` — two sections:

**"Running Tests" section** — replace the current block with:
```markdown
## Running Tests

### Unit and integration tests (no Docker needed)
```bash
./gradlew unitTest
```
Uses Spring Kafka's embedded broker. No external Kafka or Docker required.

### System tests (Docker required)
```bash
./gradlew systemTest
```
Builds all service JARs, then starts the full stack via Testcontainers + Docker Compose and runs end-to-end tests. Docker must be running.

### Single module
```bash
./gradlew :order:test
./gradlew :shared:test
```
```

**"Full Docker Workflow" section** — replace the build-and-start block with:
```markdown
**Build JARs and start everything:**
```bash
./gradlew bootJar          # build all service JARs first (required)
docker compose -f docker-compose.full.yml up --build
```
- `bootJar` compiles each service and places the JAR in `<service>/build/libs/`
- `--build` copies those JARs into Docker images and starts Kafka + all 6 services
- `OrderService` REST API available at `http://localhost:8080`
```

Update the `--build` note:
```markdown
> **Note:** Code changes require re-running `./gradlew bootJar` followed by `--build` to rebuild images. Use the daily dev workflow for fast iteration.
```

Also add a Docker cleanup note at the end of the Full Docker Workflow section:
```markdown
**Clean up Docker data volumes** (keeps images, avoids re-download on next run):
```bash
./gradlew dockerVolumeClean
```
```

**Test description:** Read through the updated README end-to-end and verify all commands match the post-FEAT-013 reality. Specifically confirm: (1) `./gradlew test` is no longer the recommended command for users; (2) the prerequisite for `docker compose up --build` clearly states `./gradlew bootJar` must come first; (3) `dockerVolumeClean` is mentioned.

**Status:** [ ] todo

---

### Slice 5: Verify and clean up

**What it delivers:** Confirms no regressions in unit tests; removes any leftover two-stage build artifacts from `.dockerignore` if present.

**Files to touch:**
- `.dockerignore` (if exists) — verify it does not exclude `*/build/libs/` (the pre-built JAR must be reachable from the Docker build context)
- Confirm `docker-compose.full.yml` `build.context: .` is still correct (it is — context is repo root, COPY paths are relative to root)

**Test description:** Run `./gradlew unitTest` locally — all unit tests pass. Run `./gradlew systemTest` locally (Docker must be running) — all system tests pass.

**Status:** [ ] todo
