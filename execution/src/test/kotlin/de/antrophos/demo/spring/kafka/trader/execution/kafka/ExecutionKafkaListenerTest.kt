package de.antrophos.demo.spring.kafka.trader.execution.kafka

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.ExecutionRequested
import de.antrophos.demo.spring.kafka.trader.execution.ExecutionService
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["execution-requests"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutionKafkaListenerTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @MockitoSpyBean
    private lateinit var executionService: ExecutionService

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        }
    }

    @Test
    fun `listener delegates ExecutionRequested to ExecutionService`() {
        val order = Order(
            id = UUID.randomUUID(),
            traderId = "trader-1",
            symbol = "AAPL",
            quantity = 100,
            side = Side.BUY
        )

        kafkaTemplate.send("execution-requests", order.id.toString(), ExecutionRequested(order))

        await atMost Duration.ofSeconds(10) untilAsserted {
            verify(executionService).execute(order)
        }
    }
}
