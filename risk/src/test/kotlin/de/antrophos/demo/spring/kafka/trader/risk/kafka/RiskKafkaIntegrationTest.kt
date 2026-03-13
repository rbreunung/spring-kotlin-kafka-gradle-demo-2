package de.antrophos.demo.spring.kafka.trader.risk.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskApproved
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskCheckRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
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

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["risk-checks", "risk-results"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RiskKafkaIntegrationTest {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    @Autowired lateinit var embeddedKafka: EmbeddedKafkaBroker
    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private lateinit var resultConsumer: Consumer<String, String>
    private lateinit var rawProducer: KafkaTemplate<String, String>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }

        val consumerProps = KafkaTestUtils.consumerProps("test-integration", "true", embeddedKafka)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        resultConsumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        resultConsumer.subscribe(listOf("risk-results"))

        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to embeddedKafka.brokersAsString,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
        )
        rawProducer = KafkaTemplate(DefaultKafkaProducerFactory(producerProps))
    }

    @AfterAll
    fun teardown() {
        resultConsumer.close()
    }

    @Test
    fun `RiskCheckRequested with quantity within limit publishes RiskApproved`() {
        val orderId = UUID.randomUUID()
        val order = Order(id = orderId, traderId = "T1", symbol = "AAPL", quantity = 100, side = Side.BUY)
        kafkaTemplate.send("risk-checks", orderId.toString(), RiskCheckRequested(order))

        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(resultConsumer, "risk-results", Duration.ofSeconds(10))
        val result = objectMapper.readValue(record.value(), RiskApproved::class.java)
        assertEquals(orderId, result.orderId)
    }

    @Test
    fun `RiskCheckRequested with quantity exceeding limit publishes RiskRejected quantity-exceeds-limit`() {
        val orderId = UUID.randomUUID()
        val order = Order(id = orderId, traderId = "T1", symbol = "AAPL", quantity = 10_001, side = Side.BUY)
        kafkaTemplate.send("risk-checks", orderId.toString(), RiskCheckRequested(order))

        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(resultConsumer, "risk-results", Duration.ofSeconds(10))
        val result = objectMapper.readValue(record.value(), RiskRejected::class.java)
        assertEquals(orderId, result.orderId)
        assertEquals("quantity-exceeds-limit", result.reason)
    }

    @Test
    fun `poison message is logged and skipped with no result published`() {
        rawProducer.send("risk-checks", UUID.randomUUID().toString(), "not-valid-json{{{")

        await.during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val records = resultConsumer.poll(Duration.ofMillis(100))
            assertEquals(0, records.count(), "Expected no records on risk-results for poison message")
        }
    }
}
