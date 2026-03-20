package de.antrophos.demo.spring.kafka.trader.notification.kafka

import de.antrophos.demo.spring.kafka.trader.notification.NotificationService
import de.antrophos.demo.spring.kafka.trader.shared.events.NotificationRequested
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Duration
import java.util.UUID

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["notifications"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationKafkaListenerTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @MockitoSpyBean
    private lateinit var notificationService: NotificationService

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        }
    }

    @Test
    fun `listener delegates NotificationRequested to NotificationService`() {
        val traderId = "trader-1"
        val orderId = UUID.randomUUID()
        val message = "Order settled"

        kafkaTemplate.send("notifications", orderId.toString(), NotificationRequested(traderId, orderId, message))

        await atMost Duration.ofSeconds(10) untilAsserted {
            verify(notificationService).notify(traderId, orderId, message)
        }
    }
}
