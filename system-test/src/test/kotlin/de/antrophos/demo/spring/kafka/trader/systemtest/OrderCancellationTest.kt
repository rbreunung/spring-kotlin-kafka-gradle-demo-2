package de.antrophos.demo.spring.kafka.trader.systemtest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
class OrderCancellationTest : SystemTestBase() {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val restTemplate = RestTemplate().apply {
        messageConverters = mutableListOf<org.springframework.http.converter.HttpMessageConverter<*>>(
            MappingJackson2HttpMessageConverter(objectMapper)
        )
    }

    @Test
    fun `cancelling a PENDING order marks order CANCELLED and saga CANCELLATION_COMPLETE`() {
        val orderId = placeOrder()

        // Cancel immediately — order is still PENDING (no Kafka round-trip has completed)
        restTemplate.delete("${orderServiceBaseUrl()}/orders/$orderId")

        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until {
            getOrder(orderId)?.status == "CANCELLED"
        }

        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until {
            getSaga(orderId)?.step == "CANCELLATION_COMPLETE"
        }

        assertEquals("CANCELLED", getOrder(orderId)?.status)
        assertEquals("CANCELLATION_COMPLETE", getSaga(orderId)?.step)
    }

    @Test
    fun `cancelling during settlement race triggers compensation when settlement completes`() {
        val orderId = placeOrder()

        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until {
            getSaga(orderId)?.step == "SETTLEMENT_REQUESTED"
        }

        // Cancel when saga is at SETTLEMENT_REQUESTED
        val saga = getSaga(orderId)
        requireNotNull(saga) { "Saga should exist at SETTLEMENT_REQUESTED" }

        restTemplate.delete("${orderServiceBaseUrl()}/orders/$orderId")

        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until {
            getSaga(orderId)?.step == "CANCEL_PENDING"
        }

        await.atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until {
            val finalSaga = getSaga(orderId)
            finalSaga?.step == "COMPENSATION_COMPLETE" || finalSaga?.step == "CANCELLATION_COMPLETE"
        }

        await.atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until {
            getOrder(orderId)?.status == "COMPENSATION_COMPLETE" || getOrder(orderId)?.status == "CANCELLED"
        }

        val finalSaga = getSaga(orderId)
        assertNotNull(finalSaga)
        assertTrue(
            finalSaga!!.step == "COMPENSATION_COMPLETE" || finalSaga.step == "CANCELLATION_COMPLETE",
            "Final saga step should be COMPENSATION_COMPLETE or CANCELLATION_COMPLETE, was: ${finalSaga.step}"
        )

        val finalOrder = getOrder(orderId)
        assertNotNull(finalOrder)
        assertTrue(
            finalOrder!!.status == "COMPENSATION_COMPLETE" || finalOrder.status == "CANCELLED",
            "Final order status should be COMPENSATION_COMPLETE or CANCELLED, was: ${finalOrder.status}"
        )
    }

    @Test
    fun `cancelling after execution complete triggers compensation flow`() {
        val orderId = placeOrder()

        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until {
            getSaga(orderId)?.step == "EXECUTION_COMPLETE"
        }

        // Cancel when saga is at EXECUTION_COMPLETE
        restTemplate.delete("${orderServiceBaseUrl()}/orders/$orderId")

        await.atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until {
            getSaga(orderId)?.step == "COMPENSATION_REQUESTED"
        }

        await.atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until {
            getSaga(orderId)?.step == "COMPENSATION_COMPLETE"
        }

        await.atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until {
            getOrder(orderId)?.status == "COMPENSATION_COMPLETE"
        }

        assertEquals("COMPENSATION_COMPLETE", getOrder(orderId)?.status)
    }

    private fun placeOrder(): UUID {
        val request = mapOf(
            "traderId" to "trader-cancel-001",
            "symbol" to "MSFT",
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

    private fun getOrder(orderId: UUID): OrderResponse? = try {
        restTemplate.getForEntity("${orderServiceBaseUrl()}/orders/$orderId", OrderResponse::class.java).body
    } catch (_: HttpClientErrorException) {
        null
    }

    data class OrderResponse(
        val id: UUID,
        val status: String,
        val createdAt: Instant,
        val updatedAt: Instant
    )

    private fun getSaga(orderId: UUID): SagaResponse? = try {
        restTemplate.getForEntity("${sagaServiceBaseUrl()}/sagas/$orderId", SagaResponse::class.java).body
    } catch (_: HttpClientErrorException.NotFound) {
        null
    } catch (_: Exception) {
        null
    }

    data class SagaResponse(
        val id: UUID,
        val step: String,
        val updatedAt: Instant
    )
}
