package de.antrophos.demo.spring.kafka.trader.systemtest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
class DataConsistencyTest : SystemTestBase() {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val restTemplate = RestTemplate().apply {
        messageConverters = mutableListOf<org.springframework.http.converter.HttpMessageConverter<*>>(
            MappingJackson2HttpMessageConverter(objectMapper)
        )
    }

    @Test
    fun `order status, saga step, and tradeId are consistent after settlement`() {
        val orderId = placeOrder()

        // Wait for full completion
        await.atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2)).until {
            try {
                val response = restTemplate.getForEntity(
                    "${sagaServiceBaseUrl()}/sagas/$orderId",
                    SagaStateResponse::class.java
                )
                response.body?.step == "SETTLED"
            } catch (_: HttpClientErrorException) {
                false
            }
        }

        val saga = restTemplate.getForEntity(
            "${sagaServiceBaseUrl()}/sagas/$orderId",
            SagaStateResponse::class.java
        ).body!!

        val order = restTemplate.getForEntity(
            "${orderServiceBaseUrl()}/orders/$orderId",
            OrderResponse::class.java
        ).body!!

        // Cross-service consistency checks
        assertEquals("SETTLED", saga.step)
        assertEquals("SETTLED", order.status)
        assertNotNull(saga.tradeId, "Saga must have a tradeId after settlement")
        assertNotNull(order.tradeId, "Order must have a tradeId after settlement")
        assertEquals(saga.tradeId, order.tradeId, "Saga and order must reference the same tradeId")
    }

    private fun placeOrder(): UUID {
        val request = mapOf(
            "traderId" to "trader-consistency-001",
            "symbol" to "META",
            "quantity" to 25,
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

    data class OrderResponse(
        val id: UUID,
        val traderId: String,
        val symbol: String,
        val quantity: Int,
        val side: String,
        val status: String,
        val tradeId: UUID?,
        val createdAt: Instant,
        val updatedAt: Instant
    )

    data class SagaStateResponse(val orderId: UUID, val step: String, val tradeId: UUID?, val updatedAt: Instant)
}
