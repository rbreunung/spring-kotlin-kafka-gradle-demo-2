package de.antrophos.demo.spring.kafka.trader.settlement

import de.antrophos.demo.spring.kafka.trader.settlement.kafka.SettlementEventPublisher
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import io.github.resilience4j.bulkhead.BulkheadRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
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
    // Tight bulkhead: only 1 concurrent call, 0 wait → 2nd call hits fallback
    "resilience4j.bulkhead.instances.settlementOperation.max-concurrent-calls=1",
    "resilience4j.bulkhead.instances.settlementOperation.max-wait-duration=0ms"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BulkheadFallbackTest {

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var settlementService: SettlementService

    @Autowired
    private lateinit var bulkheadRegistry: BulkheadRegistry

    @SpyBean
    private lateinit var eventPublisher: SettlementEventPublisher

    @org.junit.jupiter.api.BeforeAll
    fun setup() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        }
    }

    @Test
    fun `when bulkhead is full SettlementFailed with bulkhead-full is published`() {
        val orderId = UUID.randomUUID()
        val trade = Trade(
            id = UUID.randomUUID(),
            orderId = orderId,
            executedPrice = BigDecimal("50.00"),
            executedAt = Instant.now()
        )
        val order = Order(
            id = orderId,
            traderId = "bulkhead-trader",
            symbol = "BHD",
            quantity = 10,
            side = Side.BUY
        )

        // Saturate the bulkhead semaphore manually so the next settle() call hits the fallback
        val bulkhead = bulkheadRegistry.bulkhead("settlementOperation")
        bulkhead.acquirePermission()

        try {
            settlementService.settle(trade, order)
        } catch (_: Exception) {
            // BulkheadFullException or re-throw from fallback is expected
        } finally {
            bulkhead.releasePermission()
        }

        verify(eventPublisher, atLeast(1)).publishSettlementFailed(any(), eq("bulkhead-full"))
    }
}
