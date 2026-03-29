# ADR-003: Pre-built JAR Pattern for Service Dockerfiles

Status: accepted
Date: 2026-03-29
Deciders: Claude, project owner
Related Feature: FEAT-013

---

## Context

All 6 service Dockerfiles originally used a two-stage build pattern:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/service/build/libs/*.jar app.jar
```

This approach was chosen to make each Dockerfile self-contained — any environment with Docker installed could build and run a service image without a pre-installed JDK or Gradle. However, it has significant negative consequences at scale:

1. **Two distinct Docker images pulled per service build** — `eclipse-temurin:21-jdk-alpine` (build stage) and `eclipse-temurin:21-jre-alpine` (run stage). On GitHub Actions, shared runner IPs share Docker Hub's anonymous pull limit (100/6h per IP), making `toomanyrequests` errors intermittent and unpredictable.

2. **Gradle dependencies downloaded inside Docker** — the build stage copies the entire source tree and runs Gradle, which downloads all dependencies from Maven Central into the Docker layer cache. This cache is separate from the host's `~/.gradle/caches` and is discarded whenever the Docker build cache is cleared. Maven Central rate limit exposure is multiplied.

3. **Slow CI cold-start** — a cold build with no Docker layer cache takes 15–20 minutes across 6 services. This was directly observed as a cause of `ConcurrentOrdersTest` timing out in CI.

4. **`docker system prune` forces full re-download** — routine Docker cleanup commands (used to manage accumulated data volumes) also remove cached layers, triggering a full rebuild cycle on the next run.

The project already runs `./gradlew` on the host in CI (for unit tests, and now for `systemTest`) with a properly warmed `~/.gradle/caches` via `actions/cache`. Building JARs on the host and copying them into Docker is therefore both reliable and fast.

## Decision

All service Dockerfiles are simplified to single-stage: the host (or CI runner) is responsible for building the JAR via `./gradlew :<service>:bootJar` before `docker compose build` is invoked. Each Dockerfile only copies the pre-built JAR into a JRE image:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY <service>/build/libs/*.jar app.jar
EXPOSE <port>
ENTRYPOINT ["java", "-jar", "app.jar"]
```

A root-level `systemTest` Gradle task enforces the correct ordering: it declares `dependsOn` on all 6 `bootJar` tasks before `:system-test:test` runs. This makes the constraint explicit and machine-enforced rather than relying on documentation.

**This pattern is binding for all new services added to the project.**

## Consequences

**Positive:**
- Eliminates `eclipse-temurin:21-jdk-alpine` pulls entirely — Docker Hub pull count drops from 2 per service to 1 shared base layer
- Gradle runs on the host with `~/.gradle/caches` intact — no Maven Central downloads inside Docker
- `docker compose build` is near-instant after JARs are built (COPY only, no compilation)
- CI cold-start time drops from ~15–20 minutes to ~2–3 minutes for the Docker build phase
- Single-stage Dockerfiles are simpler to read and maintain

**Negative / Trade-offs:**
- Dockerfiles are no longer self-contained — `docker compose build` without prior `./gradlew bootJar` fails with a COPY error
- New services must: (1) follow the pre-built JAR Dockerfile pattern, and (2) add their `bootJar` task to the `systemTest` task's `dependsOn` list in root `build.gradle.kts`
- The `docker-compose.full.yml` `build.context: .` setup relies on the JAR being at `<service>/build/libs/*.jar` relative to the repo root — this path must be kept consistent

**Neutral:**
- `eclipse-temurin:21-jre-alpine` is still pulled from Docker Hub; a CI pre-pull step pins this to one explicit pull per run rather than N implicit pulls
- The `systemTest` task wrapper means `./gradlew :system-test:test` invoked directly (without `systemTest`) will still work but will not build JARs first — documented as a footgun in FEAT-013 spec

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| Keep two-stage builds, add Docker BuildKit `cache-from: type=gha` in CI | Reduces repeat pull cost but does not eliminate JDK image or in-Docker Gradle; adds workflow complexity (`docker/build-push-action` per service); still slow on first run |
| Mirror base images to GitHub Container Registry (`ghcr.io`) | Eliminates Docker Hub dependency entirely but requires a separate image maintenance workflow (pulling + re-pushing to ghcr.io) and adds operational overhead for a demo project |
| Authenticate with Docker Hub only (no Dockerfile change) | Raises rate limit but does not address slow build time or in-Docker Gradle dependency downloads |
| Gradle build cache in Docker using `--mount=type=cache` | BuildKit feature that caches Gradle downloads across builds; complex setup, non-standard, and still requires the JDK image |
