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
import java.util.concurrent.Executors
import java.util.concurrent.Future

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentOrdersTest : SystemTestBase() {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val restTemplate = RestTemplate().apply {
        messageConverters = mutableListOf<org.springframework.http.converter.HttpMessageConverter<*>>(
            MappingJackson2HttpMessageConverter(objectMapper)
        )
    }

    @Test
    fun `three concurrent orders each complete the full saga independently`() {
        val executor = Executors.newFixedThreadPool(3)
        val futures: List<Future<UUID>> = (1..3).map { i ->
            executor.submit<UUID> {
                placeOrder(traderId = "trader-concurrent-$i", symbol = "GOOG", quantity = 10 * i)
            }
        }
        val orderIds = futures.map { it.get() }
        executor.shutdown()

        // All three sagas must independently reach SETTLED
        await.atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2)).until {
            orderIds.all { orderId ->
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
        }

        orderIds.forEach { orderId ->
            val saga = restTemplate.getForEntity(
                "${sagaServiceBaseUrl()}/sagas/$orderId",
                SagaStateResponse::class.java
            ).body!!
            assertEquals("SETTLED", saga.step, "Saga for orderId=$orderId did not reach SETTLED")
        }
    }

    private fun placeOrder(traderId: String, symbol: String, quantity: Int): UUID {
        val request = mapOf(
            "traderId" to traderId,
            "symbol" to symbol,
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
