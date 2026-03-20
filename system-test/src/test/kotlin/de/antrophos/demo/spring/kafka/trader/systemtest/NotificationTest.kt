package de.antrophos.demo.spring.kafka.trader.systemtest

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationTest : SystemTestBase() {

    private val restTemplate = RestTemplate()

    @Test
    fun `TraderNotified is published on trader-notifications after saga reaches SETTLED`() {
        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ConsumerConfig.GROUP_ID_CONFIG to "system-test-notification-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name
        )

        KafkaConsumer<String, String>(consumerProps).use { consumer ->
            consumer.subscribe(listOf("trader-notifications"))

            val orderId = placeOrder()
            awaitSagaSettled(orderId)

            val received = await
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .until({
                    consumer.poll(Duration.ofMillis(200)).count() > 0
                }, { it })

            assertTrue(received, "Expected a TraderNotified record on trader-notifications within 30s")
        }
    }

    private fun placeOrder(): UUID {
        val request = mapOf(
            "traderId" to "trader-notification-test",
            "symbol" to "AAPL",
            "quantity" to 100,
            "side" to "BUY"
        )
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.postForEntity(
            "${orderServiceBaseUrl()}/orders",
            HttpEntity(request, headers),
            OrderResponse::class.java
        )
        assertNotNull(response.body)
        return response.body!!.id
    }

    private fun awaitSagaSettled(orderId: UUID) {
        await.atMost(120, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(2))
            .until {
                try {
                    val response = restTemplate.getForEntity(
                        "${sagaServiceBaseUrl()}/sagas/$orderId",
                        SagaStateResponse::class.java
                    )
                    response.statusCode.is2xxSuccessful && response.body?.step == "SETTLED"
                } catch (_: Exception) {
                    false
                }
            }
    }

    data class OrderResponse(val id: UUID)
    data class SagaStateResponse(val orderId: UUID, val step: String)
}
