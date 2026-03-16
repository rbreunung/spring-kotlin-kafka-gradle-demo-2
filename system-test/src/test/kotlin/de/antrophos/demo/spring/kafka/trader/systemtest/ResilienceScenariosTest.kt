package de.antrophos.demo.spring.kafka.trader.systemtest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilienceScenariosTest : SystemTestBase() {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val restTemplate = RestTemplate().apply {
        messageConverters = mutableListOf<org.springframework.http.converter.HttpMessageConverter<*>>(
            MappingJackson2HttpMessageConverter(objectMapper)
        )
    }

    @Test
    fun `system remains healthy after risk rejection — next order still settles`() {
        // Trigger risk rejection
        val rejectedOrderId = placeOrder(quantity = 10_001)
        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until {
            getSagaStep(rejectedOrderId) == "RISK_REJECTED"
        }
        assertEquals("RISK_REJECTED", getSagaStep(rejectedOrderId))

        // Place a valid order immediately after — system must still function
        val validOrderId = placeOrder(quantity = 100)
        await.atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2)).until {
            getSagaStep(validOrderId) == "SETTLED"
        }
        assertEquals("SETTLED", getSagaStep(validOrderId))
    }

    private fun placeOrder(quantity: Int): UUID {
        val request = mapOf(
            "traderId" to "trader-resilience-001",
            "symbol" to "NVDA",
            "quantity" to quantity,
            "side" to "BUY"
        )
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(
            "${orderServiceBaseUrl()}/orders",
            entity,
            OrderResponse::class.java
        )
        return response.body!!.id
    }

    private fun getSagaStep(orderId: UUID): String? = try {
        restTemplate.getForEntity(
            "${sagaServiceBaseUrl()}/sagas/$orderId",
            SagaStateResponse::class.java
        ).body?.step
    } catch (_: HttpClientErrorException) {
        null
    }

    data class OrderResponse(val id: UUID, val status: String, val createdAt: Instant, val updatedAt: Instant)
    data class SagaStateResponse(val orderId: UUID, val step: String, val tradeId: UUID?, val updatedAt: Instant)
}
