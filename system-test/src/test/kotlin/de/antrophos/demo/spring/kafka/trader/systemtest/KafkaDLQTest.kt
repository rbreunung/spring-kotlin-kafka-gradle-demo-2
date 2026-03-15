package de.antrophos.demo.spring.kafka.trader.systemtest

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaDLQTest : SystemTestBase() {

    @Test
    fun `malformed JSON on settlement-requests is routed to dlq settlements`() {
        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java
        )
        val rawProducer = KafkaTemplate(DefaultKafkaProducerFactory<String, ByteArray>(producerProps))
        rawProducer.send("settlement-requests", "dlq-test-key", "{{{ invalid json }}}".toByteArray())

        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ConsumerConfig.GROUP_ID_CONFIG to "system-test-dlq-consumer",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "true"
        )
        val consumer = DefaultKafkaConsumerFactory<String, ByteArray>(consumerProps).createConsumer()
        consumer.subscribe(listOf("dlq.settlements"))

        var received: Any? = null
        await.atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until {
            val records = consumer.poll(Duration.ofMillis(500))
            val hit = records.records("dlq.settlements").firstOrNull()
            if (hit != null) received = hit
            hit != null
        }
        consumer.close()

        assertNotNull(received, "Expected malformed message to arrive in dlq.settlements")
    }
}
