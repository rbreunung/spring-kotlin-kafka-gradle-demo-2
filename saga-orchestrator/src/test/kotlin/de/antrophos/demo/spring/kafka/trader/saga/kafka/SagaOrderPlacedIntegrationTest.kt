package de.antrophos.demo.spring.kafka.trader.saga.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStep
import de.antrophos.demo.spring.kafka.trader.saga.repository.SagaStateRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderCancelled
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderPlaced
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskCheckRequested
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["orders", "risk-checks"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SagaOrderPlacedIntegrationTest {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    @Autowired lateinit var embeddedKafka: EmbeddedKafkaBroker
    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry
    @Autowired lateinit var sagaStateRepository: SagaStateRepository

    private val objectMapper = ObjectMapper().registerKotlinModule()
    private lateinit var riskChecksConsumer: Consumer<String, String>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
        val props = KafkaTestUtils.consumerProps("test-slice2", "true", embeddedKafka)
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        riskChecksConsumer = DefaultKafkaConsumerFactory<String, String>(props).createConsumer()
        riskChecksConsumer.subscribe(listOf("risk-checks"))
    }

    private fun anOrder(orderId: UUID = UUID.randomUUID()) =
        Order(id = orderId, traderId = "T1", symbol = "AAPL", quantity = 100, side = Side.BUY)

    @Test
    fun `OrderPlaced persists saga state as RISK_REQUESTED and publishes RiskCheckRequested`() {
        val orderId = UUID.randomUUID()
        kafkaTemplate.send("orders", orderId.toString(), OrderPlaced(anOrder(orderId)))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val entity = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(entity)
            assertEquals(SagaStep.RISK_REQUESTED.name, entity.step)
        }

        // Collect all records and filter by orderId — resilient to records from other tests
        val collected = mutableListOf<RiskCheckRequested>()
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            riskChecksConsumer.poll(Duration.ofMillis(200)).forEach { record ->
                runCatching { objectMapper.readValue(record.value(), RiskCheckRequested::class.java) }
                    .onSuccess { collected.add(it) }
            }
            assertTrue(collected.any { it.order.id == orderId }, "Expected RiskCheckRequested for orderId $orderId")
        }
    }

    @Test
    fun `OrderCancelled in RISK_REQUESTED state sets saga to CANCELLATION_COMPLETE`() {
        val orderId = UUID.randomUUID()
        kafkaTemplate.send("orders", orderId.toString(), OrderPlaced(anOrder(orderId)))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            assertNotNull(sagaStateRepository.findById(orderId).orElse(null))
        }

        kafkaTemplate.send("orders", orderId.toString(), OrderCancelled(orderId))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val saga = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(saga)
            assertEquals(SagaStep.CANCELLATION_COMPLETE.name, saga.step)
        }
    }

    @Test
    fun `OrderCancelled in SETTLEMENT_REQUESTED state sets saga to CANCEL_PENDING`() {
        val orderId = UUID.randomUUID()
        kafkaTemplate.send("orders", orderId.toString(), OrderPlaced(anOrder(orderId)))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            assertNotNull(sagaStateRepository.findById(orderId).orElse(null))
        }

        // Simulate progression to SETTLEMENT_REQUESTED state
        val orderEntity = sagaStateRepository.findById(orderId).orElse(null)
        assertNotNull(orderEntity)
        val updatedEntity = sagaStateRepository.save(
            orderEntity.copy(
                step = SagaStep.SETTLEMENT_REQUESTED.name,
                tradeId = UUID.randomUUID()
            )
        )

        kafkaTemplate.send("orders", orderId.toString(), OrderCancelled(orderId))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val saga = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(saga)
            assertEquals(SagaStep.CANCEL_PENDING.name, saga.step)
        }
    }
}
