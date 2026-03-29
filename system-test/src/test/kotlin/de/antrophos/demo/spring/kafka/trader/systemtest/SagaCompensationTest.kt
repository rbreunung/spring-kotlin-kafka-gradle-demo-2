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
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SagaCompensationTest : SystemTestBase() {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val restTemplate = RestTemplate().apply {
        messageConverters = mutableListOf<org.springframework.http.converter.HttpMessageConverter<*>>(
            MappingJackson2HttpMessageConverter(objectMapper)
        )
    }

    @Test
    fun `saga compensation flow reaches COMPENSATION_COMPLETE after SettlementFailed`() {
        // trader-comp-001 is configured in docker-compose.full.yml via
        // SETTLEMENT_ALWAYS_FAIL_TRADER_IDS so settlement always fails deterministically.
        val orderId = placeOrder()

        // Wait for saga to reach COMPENSATION_COMPLETE
        await.atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(1)).until {
            try {
                getSaga(orderId)?.step == "COMPENSATION_COMPLETE"
            } catch (_: Exception) { false }
        }

        assertEquals("COMPENSATION_COMPLETE", getSaga(orderId)!!.step)

        // Wait for order status to reflect COMPENSATION_COMPLETE
        await.atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(1)).until {
            try {
                getOrder(orderId)?.status == "COMPENSATION_COMPLETE"
            } catch (_: Exception) { false }
        }

        assertEquals("COMPENSATION_COMPLETE", getOrder(orderId)!!.status)
    }

    private fun placeOrder(): UUID {
        val request = mapOf(
            "traderId" to "trader-comp-001",
            "symbol" to "TSLA",
            "quantity" to 50,
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

    private fun getSaga(orderId: UUID): SagaStateResponse? = try {
        restTemplate.getForEntity(
            "${sagaServiceBaseUrl()}/sagas/$orderId",
            SagaStateResponse::class.java
        ).body
    } catch (_: HttpClientErrorException) { null }

    private fun getOrder(orderId: UUID): OrderResponse? = try {
        restTemplate.getForEntity(
            "${orderServiceBaseUrl()}/orders/$orderId",
            OrderResponse::class.java
        ).body
    } catch (_: HttpClientErrorException) { null }

    data class OrderResponse(val id: UUID, val status: String, val tradeId: UUID?, val createdAt: Instant, val updatedAt: Instant)
    data class SagaStateResponse(val orderId: UUID, val step: String, val tradeId: UUID?, val updatedAt: Instant)
}
