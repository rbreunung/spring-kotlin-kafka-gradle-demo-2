package de.antrophos.demo.spring.kafka.trader.systemtest

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.common.ConsumerGroupState
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
            // NOTE: no withExposedService() - those use the deprecated Docker Compose V1 ambassador pattern
            // which is incompatible with Docker Compose V2 container naming conventions.

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

            composeContainer
                .withEnv("COMPOSE_PROJECT_NAME", "trader-systemtest")
                .withLocalCompose(true)
                .start()
            awaitServicesReady()
            awaitKafkaConsumerGroupsReady()
        }

        private fun awaitServicesReady() {
            val services = mapOf(
                "order-service" to "http://localhost:8080/orders",
                "saga-orchestrator" to "http://localhost:8085/sagas"
            )
            await.atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2)).until {
                services.all { (name, url) ->
                    isHttpReady(url).also { ready ->
                        if (!ready) org.slf4j.LoggerFactory.getLogger("SystemTestBase")
                            .info("Waiting for $name at $url ...")
                    }
                }
            }
        }

        private fun awaitKafkaConsumerGroupsReady() {
            val log = org.slf4j.LoggerFactory.getLogger("SystemTestBase")
            val requiredGroups = listOf(
                    "order-service",
                    "risk-service",
                    "execution-service",
                    "settlement-service",
                    "saga-orchestrator"
                )
            val adminProps = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092")
            AdminClient.create(adminProps).use { admin ->
                await.atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2)).until {
                    try {
                        val descriptions = admin.describeConsumerGroups(requiredGroups).all().get()
                        requiredGroups.all { group ->
                            val state = descriptions[group]?.state()
                            val ready = state == ConsumerGroupState.STABLE
                            if (!ready) log.info("Waiting for consumer group '$group' (state=$state) ...")
                            ready
                        }
                    } catch (_: Exception) {
                        false
                    }
                }
            }
        }

        private fun isHttpReady(url: String): Boolean = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.connect()
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }

        @JvmStatic
        fun orderServiceBaseUrl(): String = "http://localhost:8080"

        @JvmStatic
        fun sagaServiceBaseUrl(): String = "http://localhost:8085"
    }
}
