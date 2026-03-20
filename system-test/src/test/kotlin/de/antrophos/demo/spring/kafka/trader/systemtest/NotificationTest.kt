package de.antrophos.demo.spring.kafka.trader.systemtest

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationTest : SystemTestBase() {

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

            val orderId = placeOrder(traderId = "trader-notification-test")
            awaitSagaSettled(orderId)

            var received = false
            await.atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .until {
                    if (!received) {
                        received = consumer.poll(Duration.ofMillis(100))
                            .records("trader-notifications")
                            .any { it.value().contains(orderId.toString()) }
                    }
                    received
                }

            assertTrue(received, "Expected a TraderNotified record containing orderId=$orderId on trader-notifications")
        }
    }
}
