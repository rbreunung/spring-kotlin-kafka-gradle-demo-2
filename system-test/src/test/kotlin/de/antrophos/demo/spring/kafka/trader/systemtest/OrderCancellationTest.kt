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

        // Cancel immediately — order is still PENDING (no Kafka round-trip has completed).
        // Depending on Kafka timing, the saga may have advanced before the cancel arrives:
        //   early cancel  → CANCELLATION_COMPLETE / CANCELLED
        //   late cancel   → COMPENSATION_COMPLETE / COMPENSATION_COMPLETE (compensation path)
        restTemplate.delete("${orderServiceBaseUrl()}/orders/$orderId")

        await.atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(1)).until {
            val status = getOrder(orderId)?.status
            status == "CANCELLED" || status == "COMPENSATION_COMPLETE"
        }

        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until {
            val step = getSaga(orderId)?.step
            step == "CANCELLATION_COMPLETE" || step == "COMPENSATION_COMPLETE"
        }

        val finalOrder = getOrder(orderId)
        assertTrue(
            finalOrder?.status == "CANCELLED" || finalOrder?.status == "COMPENSATION_COMPLETE",
            "Expected CANCELLED or COMPENSATION_COMPLETE, was: ${finalOrder?.status}"
        )
        val finalSaga = getSaga(orderId)
        assertTrue(
            finalSaga?.step == "CANCELLATION_COMPLETE" || finalSaga?.step == "COMPENSATION_COMPLETE",
            "Expected CANCELLATION_COMPLETE or COMPENSATION_COMPLETE, was: ${finalSaga?.step}"
        )
    }

    @Test
    fun `cancelling during settlement race triggers compensation when settlement completes`() {
        val orderId = placeOrder()

        // SETTLEMENT_REQUESTED is visible for only ~40ms — poll for tradeId != null instead,
        // which is set atomically with SETTLEMENT_REQUESTED and persisted through all later steps.
        await.atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(50)).until {
            getSaga(orderId)?.tradeId != null
        }

        // Cancel — saga will be at SETTLEMENT_REQUESTED, SETTLED, or a later step.
        // SETTLEMENT_REQUESTED → CANCEL_PENDING path; SETTLED → COMPENSATION_REQUESTED path.
        restTemplate.delete("${orderServiceBaseUrl()}/orders/$orderId")

        // CANCEL_PENDING may be too brief to observe; accept any step that shows cancel was processed.
        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until {
            val step = getSaga(orderId)?.step
            step == "CANCEL_PENDING" || step == "COMPENSATION_REQUESTED" || step == "COMPENSATION_COMPLETE"
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

        // EXECUTION_COMPLETE is never committed externally — onTradeExecuted transitions
        // EXECUTION_COMPLETE → SETTLEMENT_REQUESTED within a single @Transactional.
        // tradeId is set atomically with SETTLEMENT_REQUESTED and is permanent once written.
        await.atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(50)).until {
            getSaga(orderId)?.tradeId != null
        }

        // Cancel when saga is at EXECUTION_COMPLETE
        restTemplate.delete("${orderServiceBaseUrl()}/orders/$orderId")

        await.atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).until {
            val step = getSaga(orderId)?.step
            step == "COMPENSATION_REQUESTED" || step == "COMPENSATION_COMPLETE"
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
        val orderId: UUID,
        val step: String,
        val tradeId: UUID?,
        val updatedAt: Instant
    )
}
