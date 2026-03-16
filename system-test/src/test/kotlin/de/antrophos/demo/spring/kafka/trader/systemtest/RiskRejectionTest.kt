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
class RiskRejectionTest : SystemTestBase() {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val restTemplate = RestTemplate().apply {
        messageConverters = mutableListOf<org.springframework.http.converter.HttpMessageConverter<*>>(
            MappingJackson2HttpMessageConverter(objectMapper)
        )
    }

    @Test
    fun `order with quantity exceeding risk limit is rejected and saga terminates at RISK_REJECTED`() {
        // Risk service rejects quantity > 10_000 with "quantity-exceeds-limit"
        val orderId = placeOrder(quantity = 10_001)

        await.atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until {
            try {
                val response = restTemplate.getForEntity(
                    "${sagaServiceBaseUrl()}/sagas/$orderId",
                    SagaStateResponse::class.java
                )
                response.body?.step == "RISK_REJECTED"
            } catch (_: HttpClientErrorException) {
                false
            }
        }

        val saga = restTemplate.getForEntity(
            "${sagaServiceBaseUrl()}/sagas/$orderId",
            SagaStateResponse::class.java
        ).body!!
        assertEquals("RISK_REJECTED", saga.step)
    }

    private fun placeOrder(quantity: Int): UUID {
        val request = mapOf(
            "traderId" to "trader-risk-001",
            "symbol" to "TSLA",
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

    data class OrderResponse(val id: UUID, val status: String, val createdAt: Instant, val updatedAt: Instant)
    data class SagaStateResponse(val orderId: UUID, val step: String, val tradeId: UUID?, val updatedAt: Instant)
}
