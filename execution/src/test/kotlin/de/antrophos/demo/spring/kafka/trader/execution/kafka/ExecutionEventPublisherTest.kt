package de.antrophos.demo.spring.kafka.trader.execution.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.ExecutionRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeExecuted
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
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
import java.time.Duration
import java.util.UUID

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["execution-requests", "executions"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutionEventPublisherTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private lateinit var executionsConsumer: org.apache.kafka.clients.consumer.Consumer<String, String>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        }

        val consumerProps = KafkaTestUtils.consumerProps("test-publisher", "true", embeddedKafka)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        executionsConsumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        executionsConsumer.subscribe(listOf("executions"))
    }

    @AfterAll
    fun tearDown() {
        executionsConsumer.close()
    }

    @Test
    fun `ExecutionRequested produces TradeExecuted on executions topic with matching orderId`() {
        val orderId = UUID.randomUUID()
        val order = Order(id = orderId, traderId = "trader-1", symbol = "AAPL", quantity = 100, side = Side.BUY)

        kafkaTemplate.send("execution-requests", orderId.toString(), ExecutionRequested(order))

        val record = KafkaTestUtils.getSingleRecord(executionsConsumer, "executions", Duration.ofSeconds(10))
        val result = objectMapper.readValue(record.value(), TradeExecuted::class.java)

        assertEquals(orderId, result.trade.orderId)
        assertNotNull(result.trade.id)
        assertNotNull(result.trade.executedAt)
        assertEquals(orderId.toString(), record.key())
    }
}
