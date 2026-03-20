package de.antrophos.demo.spring.kafka.trader.settlement.kafka

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementRequested
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull
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
    topics = ["settlement-requests", "settlements", "dlq.settlements"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true",
    "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
    "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
    "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
    "spring.kafka.consumer.properties.spring.json.use.type.headers=true",
    "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
    "spring.kafka.producer.properties.spring.json.add.type.headers=true",
    "settlement.simulate-failure-probability=1.0",
    "resilience4j.retry.instances.settlementOperation.max-attempts=1"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeadLetterTopicServiceFailureTest {

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        }
    }

    @Test
    fun `valid SettlementRequested that fails processing is routed to dlq settlements`() {
        val tradeId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val event = SettlementRequested(
            trade = Trade(tradeId, orderId, BigDecimal("150.00"), Instant.now()),
            order = Order(orderId, "trader-dlq-test", "AAPL", 10, Side.BUY)
        )

        kafkaTemplate.send("settlement-requests", tradeId.toString(), event)

        val consumerProps = KafkaTestUtils.consumerProps("test-dlq-service-failure", "true", embeddedKafka)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        val consumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "dlq.settlements")

        val received = await atMost Duration.ofSeconds(20) untilNotNull {
            val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1))
            records.records("dlq.settlements").firstOrNull()
        }
        consumer.close()

        assertThat(received).isNotNull
    }
}
