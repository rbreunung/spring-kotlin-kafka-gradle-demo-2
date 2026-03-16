package de.antrophos.demo.spring.kafka.trader.saga.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStep
import de.antrophos.demo.spring.kafka.trader.saga.repository.SagaStateRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.CompensationRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeVoided
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
    topics = ["orders", "risk-results", "executions", "settlements", "settlement-requests", "notifications",
        "compensation-requests", "compensation-results"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SagaOrchestratorCompensationTest {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    @Autowired lateinit var embeddedKafka: EmbeddedKafkaBroker
    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry
    @Autowired lateinit var sagaStateRepository: SagaStateRepository
    @Autowired lateinit var objectMapper: ObjectMapper

    private lateinit var compensationRequestsConsumer: Consumer<String, String>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
        compensationRequestsConsumer = rawConsumer("test-comp-requests")
            .also { it.subscribe(listOf("compensation-requests")) }
    }

    private fun rawConsumer(groupId: String): Consumer<String, String> {
        val props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafka)
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        return DefaultKafkaConsumerFactory<String, String>(props).createConsumer()
    }

    private fun seedSagaState(orderId: UUID, step: SagaStep, tradeId: UUID? = null): SagaStateEntity {
        val order = Order(id = orderId, traderId = "T1", symbol = "AAPL", quantity = 100, side = Side.BUY)
        return sagaStateRepository.save(
            SagaStateEntity(
                orderId = orderId,
                step = step.name,
                tradeId = tradeId,
                updatedAt = Instant.now(),
                orderJson = objectMapper.writeValueAsString(order)
            )
        )
    }

    @Test
    fun `SettlementFailed transitions to COMPENSATION_REQUESTED and publishes CompensationRequested`() {
        val orderId = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.SETTLEMENT_REQUESTED, tradeId = tradeId)

        kafkaTemplate.send("settlements", tradeId.toString(), SettlementFailed(tradeId, "Insufficient funds"))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val entity = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(entity)
            assertEquals(SagaStep.COMPENSATION_REQUESTED.name, entity.step)
        }

        val collected = mutableListOf<CompensationRequested>()
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            compensationRequestsConsumer.poll(Duration.ofMillis(200)).forEach { record ->
                runCatching { objectMapper.readValue(record.value(), CompensationRequested::class.java) }
                    .onSuccess { collected.add(it) }
            }
            assertTrue(collected.any { it.orderId == orderId && it.tradeId == tradeId },
                "Expected CompensationRequested for orderId=$orderId tradeId=$tradeId")
        }
    }

    @Test
    fun `TradeVoided transitions COMPENSATION_REQUESTED to COMPENSATION_COMPLETE`() {
        val orderId = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.COMPENSATION_REQUESTED, tradeId = tradeId)

        kafkaTemplate.send("compensation-results", tradeId.toString(), TradeVoided(tradeId, orderId))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val entity = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(entity)
            assertEquals(SagaStep.COMPENSATION_COMPLETE.name, entity.step)
        }
    }

    @Test
    fun `duplicate SettlementFailed after COMPENSATION_REQUESTED is skipped`() {
        val orderId = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.COMPENSATION_REQUESTED, tradeId = tradeId)

        kafkaTemplate.send("settlements", tradeId.toString(), SettlementFailed(tradeId, "Insufficient funds"))

        // Wait a moment then confirm state is still COMPENSATION_REQUESTED (not changed)
        Thread.sleep(2000)
        val entity = sagaStateRepository.findById(orderId).orElseThrow()
        assertEquals(SagaStep.COMPENSATION_REQUESTED.name, entity.step)
    }
}
