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
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HappyPathSagaFlowTest : SystemTestBase() {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val restTemplate = RestTemplate().apply {
        messageConverters = mutableListOf<org.springframework.http.converter.HttpMessageConverter<*>>(
            MappingJackson2HttpMessageConverter(objectMapper)
        )
    }

    @Test
    fun `happy path saga flow completes all steps and reaches SETTLED state`() {
        val orderId = placeOrder()

        val order = getOrder(orderId)
        assertNotNull(order)
        assertEquals("PENDING", order!!.status)

        val saga = awaitSagaCompletion(orderId)
        assertNotNull(saga)
        assertEquals("SETTLED", saga.step)
        assertNotNull(saga.tradeId)

        val finalOrder = getOrder(orderId)
        assertNotNull(finalOrder)
        assertEquals("SETTLED", finalOrder!!.status)
        assertNotNull(finalOrder.tradeId)
        assertEquals(saga.tradeId, finalOrder.tradeId)
    }

    private fun placeOrder(): UUID {
        val request = mapOf(
            "traderId" to "trader-001",
            "symbol" to "AAPL",
            "quantity" to 100,
            "side" to "BUY"
        )

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(request, headers)

        val response = restTemplate.postForEntity(
            "${orderServiceBaseUrl()}/orders",
            entity,
            OrderResponse::class.java
        )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body)
        return response.body!!.id
    }

    private fun getOrder(orderId: UUID): OrderResponse? {
        val response = restTemplate.getForEntity(
            "${orderServiceBaseUrl()}/orders/$orderId",
            OrderResponse::class.java
        )
        return if (response.statusCode.is2xxSuccessful) response.body else null
    }

    private fun awaitSagaCompletion(orderId: UUID, timeout: Long = 60): SagaStateResponse {
        await.atMost(timeout, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(2))
            .until {
                val response = restTemplate.getForEntity(
                    "${sagaServiceBaseUrl()}/sagas/$orderId",
                    SagaStateResponse::class.java
                )
                response.statusCode.is2xxSuccessful && response.body?.step == "SETTLED"
            }

        return restTemplate.getForEntity(
            "${sagaServiceBaseUrl()}/sagas/$orderId",
            SagaStateResponse::class.java
        ).body!!
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

    data class SagaStateResponse(
        val orderId: UUID,
        val step: String,
        val tradeId: UUID?,
        val updatedAt: Instant
    )
}
