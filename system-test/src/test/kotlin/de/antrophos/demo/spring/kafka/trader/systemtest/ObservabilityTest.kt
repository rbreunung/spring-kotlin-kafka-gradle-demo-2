package de.antrophos.demo.spring.kafka.trader.systemtest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI

class ObservabilityTest : SystemTestBase() {

    @Test
    fun `all services report health UP`() {
        val endpoints = mapOf(
            "order-service"        to "http://localhost:8080/actuator/health",
            "risk-service"         to "http://localhost:8081/actuator/health",
            "execution-service"    to "http://localhost:8082/actuator/health",
            "settlement-service"   to "http://localhost:8083/actuator/health",
            "notification-service" to "http://localhost:8084/actuator/health",
            "saga-orchestrator"    to "http://localhost:8085/actuator/health"
        )

        endpoints.forEach { (name, url) ->
            val (code, body) = httpGet(url)
            assertThat(code)
                .describedAs("$name health endpoint returned unexpected status")
                .isEqualTo(200)
            assertThat(body)
                .describedAs("$name health response does not contain status:UP")
                .contains("\"status\":\"UP\"")
        }
    }

    private fun httpGet(url: String): Pair<Int, String> {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.connect()
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        return code to body
    }
}
