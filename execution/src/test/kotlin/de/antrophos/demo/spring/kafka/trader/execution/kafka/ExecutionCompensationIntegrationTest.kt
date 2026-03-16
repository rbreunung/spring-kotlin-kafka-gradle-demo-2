package de.antrophos.demo.spring.kafka.trader.execution.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.antrophos.demo.spring.kafka.trader.execution.domain.TradeEntity
import de.antrophos.demo.spring.kafka.trader.execution.domain.TradeStatus
import de.antrophos.demo.spring.kafka.trader.execution.repository.TradeRepository
import de.antrophos.demo.spring.kafka.trader.shared.events.CompensationRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeVoided
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["execution-requests", "executions", "compensation-requests", "compensation-results"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutionCompensationIntegrationTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var tradeRepository: TradeRepository

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private lateinit var compensationResultsConsumer: org.apache.kafka.clients.consumer.Consumer<String, String>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        }

        val consumerProps = KafkaTestUtils.consumerProps("test-compensation", "true", embeddedKafka)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        compensationResultsConsumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        compensationResultsConsumer.subscribe(listOf("compensation-results"))
    }

    @AfterAll
    fun tearDown() {
        compensationResultsConsumer.close()
    }

    private fun savedTrade(tradeId: UUID, orderId: UUID): TradeEntity {
        return tradeRepository.save(
            TradeEntity(
                id = tradeId,
                orderId = orderId,
                executedPrice = BigDecimal("150.00"),
                executedAt = Instant.now(),
                status = TradeStatus.EXECUTED.name
            )
        )
    }

    @Test
    fun `CompensationRequested marks TradeEntity VOIDED and publishes TradeVoided`() {
        val tradeId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        savedTrade(tradeId, orderId)

        kafkaTemplate.send("compensation-requests", orderId.toString(), CompensationRequested(orderId, tradeId, "Settlement failed"))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val entity = tradeRepository.findById(tradeId).orElse(null)
            assertNotNull(entity)
            assertEquals(TradeStatus.VOIDED.name, entity!!.status)
        }

        val collected = mutableListOf<TradeVoided>()
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            compensationResultsConsumer.poll(Duration.ofMillis(200)).forEach { record ->
                runCatching { objectMapper.readValue(record.value(), TradeVoided::class.java) }
                    .onSuccess { collected.add(it) }
            }
            assert(collected.any { it.tradeId == tradeId && it.orderId == orderId }) {
                "Expected TradeVoided for tradeId=$tradeId"
            }
        }
    }

    @Test
    fun `CompensationRequested for unknown tradeId still publishes TradeVoided`() {
        val tradeId = UUID.randomUUID()
        val orderId = UUID.randomUUID()

        kafkaTemplate.send("compensation-requests", orderId.toString(), CompensationRequested(orderId, tradeId, "Settlement failed"))

        val collected = mutableListOf<TradeVoided>()
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            compensationResultsConsumer.poll(Duration.ofMillis(200)).forEach { record ->
                runCatching { objectMapper.readValue(record.value(), TradeVoided::class.java) }
                    .onSuccess { collected.add(it) }
            }
            assert(collected.any { it.tradeId == tradeId }) {
                "Expected TradeVoided even for unknown tradeId=$tradeId"
            }
        }
    }
}
