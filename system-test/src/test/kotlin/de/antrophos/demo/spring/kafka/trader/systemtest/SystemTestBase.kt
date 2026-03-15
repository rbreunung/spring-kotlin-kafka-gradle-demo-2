package de.antrophos.demo.spring.kafka.trader.systemtest

import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

abstract class SystemTestBase {

    companion object {
        @JvmStatic
        private val projectRoot = File(System.getProperty("user.dir")).parentFile
        @JvmStatic
        private val composeFile = File(projectRoot, "docker-compose.full.yml")

        @JvmStatic
        private val composeContainer = DockerComposeContainer(composeFile)
            .withLogConsumer("kafka", Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("kafka")))
            .withLogConsumer("order-service", Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("order-service")))
            .withLogConsumer("risk-service", Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("risk-service")))
            .withLogConsumer("execution-service", Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("execution-service")))
            .withLogConsumer("settlement-service", Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("settlement-service")))
            .withLogConsumer("notification-service", Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("notification-service")))
            .withLogConsumer("saga-orchestrator", Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("saga-orchestrator")))
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:19092,PLAINTEXT_HOST://localhost:9092")

        @BeforeAll
        @JvmStatic
        fun setup() {
            checkDockerAvailability()
            startComposeStack()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            composeContainer.stop()
        }

        private fun checkDockerAvailability() {
            try {
                val dockerCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "docker.exe" else "docker"
                val process = ProcessBuilder(dockerCommand, "info").redirectErrorStream(true).start()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    throw IllegalStateException(
                        "Docker is not available or not responding. " +
                        "System tests require Docker to be installed and running. " +
                        "Please install Docker Desktop or ensure the Docker daemon is running."
                    )
                }
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Docker is not available. " +
                    "System tests require Docker to be installed and running. " +
                    "Please install Docker Desktop or ensure the Docker daemon is running. " +
                    "Error: ${e.message}",
                    e
                )
            }
        }

        private fun startComposeStack() {
            if (!composeFile.exists()) {
                throw IllegalStateException(
                    "docker-compose.full.yml not found. " +
                    "System tests require the docker-compose.full.yml file in the project root. " +
                    "Expected path: ${composeFile.absolutePath}"
                )
            }

            composeContainer.withStartupTimeout(Duration.ofSeconds(300))
                .withEnv("COMPOSE_PROJECT_NAME", "trader-systemtest")
                .withLocalCompose(true)
                .start()

            awaitServicesReady()
        }

        private fun awaitServicesReady() {
            await.atMost(Duration.ofSeconds(300))
                .pollInterval(Duration.ofSeconds(2))
                .until {
                    isHttpReady(orderServiceBaseUrl() + "/orders") &&
                    isHttpReady(sagaServiceBaseUrl() + "/sagas")
                }
        }

        private fun isHttpReady(urlStr: String): Boolean {
            return try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                val status = conn.responseCode
                conn.disconnect()
                status < 500
            } catch (e: Exception) {
                false
            }
        }

        @DynamicPropertySource
        @JvmStatic
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            val kafkaHost = composeContainer.getServiceHost("kafka", 9092)
            val kafkaPort = composeContainer.getServicePort("kafka", 9092)
            registry.add("spring.kafka.bootstrap-servers") { "$kafkaHost:$kafkaPort" }
        }

        @JvmStatic
        fun orderServiceBaseUrl(): String = "http://localhost:8080"

        @JvmStatic
        fun sagaServiceBaseUrl(): String = "http://localhost:8085"
    }
}
