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
import io.micrometer.core.instrument.MeterRegistry
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
    topics = ["settlement-requests", "settlements"]
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
    "settlement.simulate-failure-probability=1.0"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryFallbackTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var positionRepository: de.antrophos.demo.spring.kafka.trader.settlement.repository.PositionRepository

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        }
    }

    @Test
    fun `when all retries exhausted SettlementFailed is published and position is not updated`() {
        val orderId = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        val trade = Trade(
            id = tradeId,
            orderId = orderId,
            executedPrice = BigDecimal("100.00"),
            executedAt = Instant.now()
        )
        val order = Order(
            id = orderId,
            traderId = "retry-trader",
            symbol = "RETRY",
            quantity = 50,
            side = Side.BUY
        )

        val consumerProps = KafkaTestUtils.consumerProps("test-retry-consumer", "true", embeddedKafka)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        val consumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "settlements")

        kafkaTemplate.send("settlement-requests", orderId.toString(), SettlementRequested(trade, order))

        val received = await atMost Duration.ofSeconds(30) untilNotNull {
            val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1))
            records.records("settlements").firstOrNull()
        }
        consumer.close()

        assertThat(received.value()).contains(tradeId.toString())
        val typeHeader = received.headers().lastHeader("__TypeId__")
        assertThat(typeHeader).isNotNull
        assertThat(String(typeHeader!!.value())).contains("SettlementFailed")

        val position = positionRepository.findByTraderIdAndSymbol("retry-trader", "RETRY")
        assertThat(position).isNull()

        val counter = meterRegistry.find("settlement.attempts.total").tag("outcome", "failure").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isGreaterThanOrEqualTo(1.0)
    }
}
