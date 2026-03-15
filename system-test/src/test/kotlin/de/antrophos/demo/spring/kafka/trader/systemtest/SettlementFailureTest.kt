package de.antrophos.demo.spring.kafka.trader.systemtest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertEquals
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
class SettlementFailureTest : SystemTestBase() {

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
    fun `saga terminal state SETTLED is protected against late SettlementFailed events`() {
        val orderId = placeOrder()

        // Wait for saga to reach SETTLED (full happy path)
        await.atMost(120, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(2)).until {
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
        assertEquals("SETTLED", saga.step)
        val tradeId = saga.tradeId!!

        // Inject a late SettlementFailed for the already-settled trade
        kafkaTemplate.send("settlements", tradeId.toString(), SettlementFailed(tradeId, "test-late-failure"))

        // Wait a few seconds then verify saga is still SETTLED (terminal state protection)
        Thread.sleep(5_000)
        val sagaAfter = restTemplate.getForEntity(
            "${sagaServiceBaseUrl()}/sagas/$orderId",
            SagaStateResponse::class.java
        ).body!!
        assertEquals("SETTLED", sagaAfter.step)
    }

    private fun placeOrder(): UUID {
        val request = mapOf(
            "traderId" to "trader-settle-001",
            "symbol" to "AMZN",
            "quantity" to 75,
            "side" to "SELL"
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
