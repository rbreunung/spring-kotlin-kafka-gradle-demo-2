package de.antrophos.demo.spring.kafka.trader.saga.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStep
import de.antrophos.demo.spring.kafka.trader.saga.repository.SagaStateRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.ExecutionRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskApproved
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
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
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["risk-results", "execution-requests"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SagaRiskResultsIntegrationTest {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    @Autowired lateinit var embeddedKafka: EmbeddedKafkaBroker
    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry
    @Autowired lateinit var sagaStateRepository: SagaStateRepository

    private val objectMapper = ObjectMapper().registerKotlinModule()
    private lateinit var executionRequestsConsumer: Consumer<String, String>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
        val props = KafkaTestUtils.consumerProps("test-slice3", "true", embeddedKafka)
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        executionRequestsConsumer = DefaultKafkaConsumerFactory<String, String>(props).createConsumer()
        executionRequestsConsumer.subscribe(listOf("execution-requests"))
    }

    private fun seedSagaState(orderId: UUID, step: SagaStep): SagaStateEntity {
        val order = Order(id = orderId, traderId = "T1", symbol = "AAPL", quantity = 100, side = Side.BUY)
        return sagaStateRepository.save(
            SagaStateEntity(
                orderId = orderId,
                step = step.name,
                updatedAt = Instant.now(),
                orderJson = objectMapper.writeValueAsString(order)
            )
        )
    }

    @Test
    fun `RiskApproved transitions to EXECUTION_REQUESTED and publishes ExecutionRequested`() {
        val orderId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.RISK_REQUESTED)

        kafkaTemplate.send("risk-results", orderId.toString(), RiskApproved(orderId))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val entity = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(entity)
            assertEquals(SagaStep.EXECUTION_REQUESTED.name, entity.step)
        }

        val collected = mutableListOf<ExecutionRequested>()
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            executionRequestsConsumer.poll(Duration.ofMillis(200)).forEach { record ->
                runCatching { objectMapper.readValue(record.value(), ExecutionRequested::class.java) }
                    .onSuccess { collected.add(it) }
            }
            assertTrue(collected.any { it.order.id == orderId }, "Expected ExecutionRequested for orderId $orderId")
        }
    }

    @Test
    fun `RiskRejected transitions to RISK_REJECTED terminal state`() {
        val orderId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.RISK_REQUESTED)

        kafkaTemplate.send("risk-results", orderId.toString(), RiskRejected(orderId, "quantity-exceeds-limit"))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val entity = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(entity)
            assertEquals(SagaStep.RISK_REJECTED.name, entity.step)
        }
    }

    @Test
    fun `RiskApproved for unknown orderId is silently skipped`() {
        val unknownId = UUID.randomUUID()
        kafkaTemplate.send("risk-results", unknownId.toString(), RiskApproved(unknownId))

        await.during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertTrue(sagaStateRepository.findById(unknownId).isEmpty)
        }
    }

    @Test
    fun `RiskApproved for terminal saga state is ignored`() {
        val orderId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.RISK_REJECTED)

        kafkaTemplate.send("risk-results", orderId.toString(), RiskApproved(orderId))

        await.during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val entity = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(entity)
            assertEquals(SagaStep.RISK_REJECTED.name, entity.step)
        }
    }
}
