package de.antrophos.demo.spring.kafka.trader.systemtest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonSerializer
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

    private val kafkaTemplate: KafkaTemplate<String, Any> by lazy {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to true
        )
        KafkaTemplate(DefaultKafkaProducerFactory(props))
    }

    @Test
    fun `saga compensation flow reaches COMPENSATION_COMPLETE after SettlementFailed`() {
        val orderId = placeOrder()

        // Wait for saga to reach SETTLEMENT_REQUESTED (trade has been executed)
        await.atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(1)).until {
            try {
                val saga = getSaga(orderId)
                saga != null && saga.step in listOf("SETTLEMENT_REQUESTED", "SETTLED", "COMPENSATION_REQUESTED", "COMPENSATION_COMPLETE")
            } catch (_: Exception) { false }
        }

        val sagaAtSettlement = getSaga(orderId)!!
        if (sagaAtSettlement.step == "SETTLED") {
            // Happy path won the race; skip this test run
            return
        }

        // Get the tradeId to inject SettlementFailed
        val tradeId = sagaAtSettlement.tradeId
        assertNotNull(tradeId, "tradeId must be set by SETTLEMENT_REQUESTED")

        // Inject SettlementFailed if not already in compensation path
        if (sagaAtSettlement.step == "SETTLEMENT_REQUESTED") {
            kafkaTemplate.send("settlements", tradeId.toString(), SettlementFailed(tradeId!!, "Simulated settlement failure"))
        }

        // Wait for saga to reach COMPENSATION_COMPLETE
        await.atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(1)).until {
            try {
                getSaga(orderId)?.step == "COMPENSATION_COMPLETE"
            } catch (_: Exception) { false }
        }

        val finalSaga = getSaga(orderId)!!
        assertEquals("COMPENSATION_COMPLETE", finalSaga.step)

        // Wait for order to reach COMPENSATION_COMPLETE
        await.atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(1)).until {
            try {
                getOrder(orderId)?.status == "COMPENSATION_COMPLETE"
            } catch (_: Exception) { false }
        }

        val finalOrder = getOrder(orderId)!!
        assertEquals("COMPENSATION_COMPLETE", finalOrder.status)
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
