plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    id("org.springframework.boot") version "3.5.11" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

description = "Trade Execution Platform — banking-domain saga orchestration demo with Kafka exactly-once and Resilience4j"

allprojects {
    group = "de.antrophos.demo.spring.kafka"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

tasks.register("unitTest") {
    description = "Run all unit tests, excluding system tests"
    group = "verification"
    dependsOn(subprojects
        .filter { it.name != "system-test" }
        .map { "${it.path}:test" })
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
