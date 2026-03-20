package de.antrophos.demo.spring.kafka.trader.notification.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import de.antrophos.demo.spring.kafka.trader.shared.events.TraderNotified
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

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `publishTraderNotified sends TraderNotified event to trader-notifications topic`() {
        val traderId = "trader-test"
        val orderId = UUID.randomUUID()
        val message = "Order settled"

        val consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka)
        val jsonDeserializer = JsonDeserializer(TraderNotified::class.java, objectMapper)
        jsonDeserializer.addTrustedPackages("*")
        val consumer = DefaultKafkaConsumerFactory<String, TraderNotified>(
            consumerProps, StringDeserializer(), jsonDeserializer
        ).createConsumer()
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "trader-notifications")

        publisher.publishTraderNotified(traderId, orderId, message)

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5))
        consumer.close()

        val record = records.records("trader-notifications").firstOrNull()
        assertNotNull(record, "Expected a TraderNotified record on trader-notifications")
        assertEquals(orderId.toString(), record.key())
        assertEquals(TraderNotified(traderId, orderId, message), record.value())
    }
}
