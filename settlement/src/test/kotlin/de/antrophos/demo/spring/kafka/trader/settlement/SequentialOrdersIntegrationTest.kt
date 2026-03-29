package de.antrophos.demo.spring.kafka.trader.settlement

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
    "spring.kafka.producer.properties.spring.json.add.type.headers=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SequentialOrdersIntegrationTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        }
    }

    @Test
    fun `second SettlementRequested for same traderId and symbol also produces PositionSettled`() {
        val traderId = "seq-trader"
        val symbol = "SEQ"

        val consumerProps = KafkaTestUtils.consumerProps("test-sequential-consumer", "true", embeddedKafka)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        val consumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "settlements")

        // Order 1 — creates new position (createPosition path)
        val orderId1 = UUID.randomUUID()
        val tradeId1 = UUID.randomUUID()
        kafkaTemplate.send(
            "settlement-requests", orderId1.toString(),
            SettlementRequested(
                trade = Trade(tradeId1, orderId1, BigDecimal("100.00"), Instant.now()),
                order = Order(orderId1, traderId, symbol, 100, Side.BUY)
            )
        )

        val record1 = await atMost Duration.ofSeconds(30) untilNotNull {
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1)).records("settlements").firstOrNull()
        }
        val typeHeader1 = record1.headers().lastHeader("__TypeId__")?.value()?.let { String(it) }
        assertThat(typeHeader1)
            .withFailMessage("Order 1 should produce PositionSettled but got: $typeHeader1")
            .contains("PositionSettled")

        // Order 2 — updates existing position for same traderId/symbol (applyTrade path — this is the failing path)
        val orderId2 = UUID.randomUUID()
        val tradeId2 = UUID.randomUUID()
        kafkaTemplate.send(
            "settlement-requests", orderId2.toString(),
            SettlementRequested(
                trade = Trade(tradeId2, orderId2, BigDecimal("110.00"), Instant.now()),
                order = Order(orderId2, traderId, symbol, 50, Side.BUY)
            )
        )

        val record2 = await atMost Duration.ofSeconds(30) untilNotNull {
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1)).records("settlements").firstOrNull()
        }
        consumer.close()

        val typeHeader2 = record2.headers().lastHeader("__TypeId__")?.value()?.let { String(it) }
        assertThat(typeHeader2)
            .withFailMessage("Order 2 should produce PositionSettled but got: $typeHeader2")
            .contains("PositionSettled")
    }
}
