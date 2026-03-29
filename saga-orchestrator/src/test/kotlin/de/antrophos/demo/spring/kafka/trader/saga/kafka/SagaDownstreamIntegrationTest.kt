package de.antrophos.demo.spring.kafka.trader.saga.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStep
import de.antrophos.demo.spring.kafka.trader.saga.repository.SagaStateRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import de.antrophos.demo.spring.kafka.trader.shared.events.CompensationRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.NotificationRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.PositionSettled
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeExecuted
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
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["executions", "settlements", "settlement-requests", "notifications", "compensation-requests", "compensation-results"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SagaDownstreamIntegrationTest {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    @Autowired lateinit var embeddedKafka: EmbeddedKafkaBroker
    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry
    @Autowired lateinit var sagaStateRepository: SagaStateRepository
    @Autowired lateinit var objectMapper: ObjectMapper

    private lateinit var settlementRequestsConsumer: Consumer<String, String>
    private lateinit var notificationsConsumer: Consumer<String, String>
    private lateinit var compensationRequestsConsumer: Consumer<String, String>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
        settlementRequestsConsumer = rawConsumer("test-slice4-settlement")
            .also { it.subscribe(listOf("settlement-requests")) }
        notificationsConsumer = rawConsumer("test-slice4-notification")
            .also { it.subscribe(listOf("notifications")) }
        compensationRequestsConsumer = rawConsumer("test-slice4-compensation")
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

    private fun aTrade(orderId: UUID, tradeId: UUID = UUID.randomUUID()) =
        Trade(id = tradeId, orderId = orderId, executedPrice = BigDecimal("150.00"), executedAt = Instant.now())

    @Test
    fun `TradeExecuted transitions to SETTLEMENT_REQUESTED and publishes SettlementRequested`() {
        val orderId = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.EXECUTION_REQUESTED)
        val trade = aTrade(orderId, tradeId)

        kafkaTemplate.send("executions", orderId.toString(), TradeExecuted(trade))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val entity = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(entity)
            assertEquals(SagaStep.SETTLEMENT_REQUESTED.name, entity.step)
            assertEquals(tradeId, entity.tradeId)
        }

        val collected = mutableListOf<SettlementRequested>()
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            settlementRequestsConsumer.poll(Duration.ofMillis(200)).forEach { record ->
                runCatching { objectMapper.readValue(record.value(), SettlementRequested::class.java) }
                    .onSuccess { collected.add(it) }
            }
            assertTrue(collected.any { it.trade.id == tradeId }, "Expected SettlementRequested for tradeId $tradeId")
        }
    }

    @Test
    fun `PositionSettled transitions to SETTLED and publishes NotificationRequested`() {
        val orderId = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.SETTLEMENT_REQUESTED, tradeId = tradeId)

        kafkaTemplate.send("settlements", tradeId.toString(), PositionSettled(tradeId, de.antrophos.demo.spring.kafka.trader.shared.domain.Position("T1", "AAPL", 100, BigDecimal("150.00"))))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val entity = sagaStateRepository.findById(orderId).orElse(null)
            assertNotNull(entity)
            assertEquals(SagaStep.SETTLED.name, entity.step)
        }

        val collected = mutableListOf<NotificationRequested>()
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            notificationsConsumer.poll(Duration.ofMillis(200)).forEach { record ->
                runCatching { objectMapper.readValue(record.value(), NotificationRequested::class.java) }
                    .onSuccess { collected.add(it) }
            }
            assertTrue(collected.any { it.orderId == orderId }, "Expected NotificationRequested for orderId $orderId")
        }
    }

    @Test
    fun `SettlementFailed transitions through SETTLEMENT_FAILED to COMPENSATION_REQUESTED and publishes CompensationRequested`() {
        val orderId = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        seedSagaState(orderId, SagaStep.SETTLEMENT_REQUESTED, tradeId = tradeId)

        kafkaTemplate.send("settlements", tradeId.toString(), SettlementFailed(tradeId, orderId, "Insufficient funds"))

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
            assertTrue(collected.any { it.orderId == orderId && it.tradeId == tradeId }, "Expected CompensationRequested for orderId $orderId")
        }
    }
}
