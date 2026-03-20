package de.antrophos.demo.spring.kafka.trader.notification.kafka

import de.antrophos.demo.spring.kafka.trader.shared.events.TraderNotified
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = ["trader-notifications"])
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=false"
])
@DirtiesContext
class NotificationEventPublisherTest {

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var publisher: NotificationEventPublisher

    @Test
    fun `publishTraderNotified sends TraderNotified event to trader-notifications topic`() {
        val traderId = "trader-test"
        val orderId = UUID.randomUUID()
        val message = "Order settled"

        publisher.publishTraderNotified(traderId, orderId, message)

        val consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java
        consumerProps[JsonDeserializer.TRUSTED_PACKAGES] = "*"
        consumerProps[JsonDeserializer.VALUE_DEFAULT_TYPE] = TraderNotified::class.java.name

        val consumer = DefaultKafkaConsumerFactory<String, TraderNotified>(consumerProps).createConsumer()
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "trader-notifications")

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5))
        consumer.close()

        val record = records.records("trader-notifications").firstOrNull()
        assertNotNull(record, "Expected a TraderNotified record on trader-notifications")
        assertEquals(orderId.toString(), record.key())
        assertEquals(TraderNotified(traderId, orderId, message), record.value())
    }
}
