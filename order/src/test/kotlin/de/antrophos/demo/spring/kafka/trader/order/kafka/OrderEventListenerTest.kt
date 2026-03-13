package de.antrophos.demo.spring.kafka.trader.order.kafka

import de.antrophos.demo.spring.kafka.trader.order.domain.OrderEntity
import de.antrophos.demo.spring.kafka.trader.order.domain.OrderStatus
import de.antrophos.demo.spring.kafka.trader.order.repository.OrderRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Position
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import de.antrophos.demo.spring.kafka.trader.shared.events.PositionSettled
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskApproved
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeExecuted
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["orders", "risk-results", "executions", "settlements"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
])
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderEventListenerTest {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @BeforeAll
    fun waitForListeners() {
        listenerRegistry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
    }

    private fun savedOrder(
        tradeId: UUID? = null,
        status: String = OrderStatus.PENDING.name
    ): OrderEntity {
        val entity = OrderEntity(
            id = UUID.randomUUID(),
            traderId = "T1",
            symbol = "AAPL",
            quantity = 10,
            side = Side.BUY.name,
            status = status,
            tradeId = tradeId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        return orderRepository.save(entity)
    }

    @Test
    fun `RiskApproved transitions PENDING to RISK_APPROVED`() {
        val order = savedOrder()
        kafkaTemplate.send("risk-results", order.id.toString(), RiskApproved(order.id))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val updated = orderRepository.findById(order.id).orElseThrow()
            assertEquals(OrderStatus.RISK_APPROVED.name, updated.status)
        }
    }

    @Test
    fun `RiskRejected transitions PENDING to RISK_REJECTED`() {
        val order = savedOrder()
        kafkaTemplate.send("risk-results", order.id.toString(), RiskRejected(order.id, "Limit exceeded"))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val updated = orderRepository.findById(order.id).orElseThrow()
            assertEquals(OrderStatus.RISK_REJECTED.name, updated.status)
        }
    }

    @Test
    fun `TradeExecuted transitions RISK_APPROVED to EXECUTED and saves tradeId`() {
        val order = savedOrder(status = OrderStatus.RISK_APPROVED.name)
        val tradeId = UUID.randomUUID()
        val trade = Trade(id = tradeId, orderId = order.id, executedPrice = BigDecimal("150.00"), executedAt = Instant.now())
        kafkaTemplate.send("executions", order.id.toString(), TradeExecuted(trade))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val updated = orderRepository.findById(order.id).orElseThrow()
            assertEquals(OrderStatus.EXECUTED.name, updated.status)
            assertEquals(tradeId, updated.tradeId)
        }
    }

    @Test
    fun `PositionSettled transitions EXECUTED to SETTLED via tradeId lookup`() {
        val tradeId = UUID.randomUUID()
        val order = savedOrder(tradeId = tradeId, status = OrderStatus.EXECUTED.name)
        val position = Position(traderId = "T1", symbol = "AAPL", quantity = 10, avgCost = BigDecimal("150.00"))
        kafkaTemplate.send("settlements", tradeId.toString(), PositionSettled(tradeId, position))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val updated = orderRepository.findById(order.id).orElseThrow()
            assertEquals(OrderStatus.SETTLED.name, updated.status)
        }
    }

    @Test
    fun `SettlementFailed transitions EXECUTED to EXECUTION_FAILED via tradeId lookup`() {
        val tradeId = UUID.randomUUID()
        val order = savedOrder(tradeId = tradeId, status = OrderStatus.EXECUTED.name)
        kafkaTemplate.send("settlements", tradeId.toString(), SettlementFailed(tradeId, "Insufficient funds"))

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val updated = orderRepository.findById(order.id).orElseThrow()
            assertEquals(OrderStatus.EXECUTION_FAILED.name, updated.status)
        }
    }

    @Test
    fun `duplicate event on terminal order is skipped`() {
        val order = savedOrder(status = OrderStatus.SETTLED.name)
        kafkaTemplate.send("risk-results", order.id.toString(), RiskApproved(order.id))

        Thread.sleep(2000) // brief pause — state should NOT change
        val updated = orderRepository.findById(order.id).orElseThrow()
        assertEquals(OrderStatus.SETTLED.name, updated.status)
    }
}
