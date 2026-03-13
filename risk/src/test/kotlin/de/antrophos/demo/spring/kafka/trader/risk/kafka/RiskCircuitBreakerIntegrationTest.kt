package de.antrophos.demo.spring.kafka.trader.risk.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskCheckRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterAll
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

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["risk-checks", "risk-results"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true",
    "risk.simulate-failure-probability=1.0"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RiskCircuitBreakerIntegrationTest {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    @Autowired lateinit var embeddedKafka: EmbeddedKafkaBroker
    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private lateinit var resultConsumer: Consumer<String, String>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }

        val consumerProps = KafkaTestUtils.consumerProps("test-cb", "true", embeddedKafka)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        resultConsumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        resultConsumer.subscribe(listOf("risk-results"))
    }

    @AfterAll
    fun teardown() {
        resultConsumer.close()
    }

    private fun publishAndConsume(quantity: Int): RiskRejected {
        val orderId = UUID.randomUUID()
        val order = Order(id = orderId, traderId = "T1", symbol = "AAPL", quantity = quantity, side = Side.BUY)
        kafkaTemplate.send("risk-checks", orderId.toString(), RiskCheckRequested(order))
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(resultConsumer, "risk-results", Duration.ofSeconds(10))
        return objectMapper.readValue(record.value(), RiskRejected::class.java)
    }

    @Test
    fun `CB opens after 5 failures and subsequent calls receive risk-service-unavailable`() {
        // Calls 1-5: CB CLOSED, evaluate() throws → fallback publishes "evaluation-failed"
        repeat(5) { i ->
            val result = publishAndConsume(100)
            assertEquals("evaluation-failed", result.reason,
                "Call ${i + 1}: expected evaluation-failed while CB is CLOSED")
        }

        // Call 6: CB is now OPEN → fallback publishes "risk-service-unavailable"
        val result = publishAndConsume(100)
        assertEquals("risk-service-unavailable", result.reason,
            "Call 6: expected risk-service-unavailable from OPEN CB fallback")
    }
}
