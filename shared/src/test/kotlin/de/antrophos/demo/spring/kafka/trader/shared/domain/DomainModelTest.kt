package de.antrophos.demo.spring.kafka.trader.shared.domain

import de.antrophos.demo.spring.kafka.trader.shared.events.OrderCancelled
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DomainModelTest {

    @Test
    fun `Order data class equality and copy`() {
        val id = UUID.randomUUID()
        val order = Order(id = id, traderId = "T001", symbol = "AAPL", quantity = 100, side = Side.BUY)
        val copy = order.copy(quantity = 200)

        assertEquals(order.id, copy.id)
        assertEquals(order.traderId, copy.traderId)
        assertNotEquals(order.quantity, copy.quantity)
        assertEquals(200, copy.quantity)
    }

    @Test
    fun `Trade data class equality`() {
        val orderId = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        val trade = Trade(id = tradeId, orderId = orderId, executedPrice = BigDecimal("150.25"), executedAt = Instant.now())

        assertEquals(tradeId, trade.id)
        assertEquals(orderId, trade.orderId)
    }

    @Test
    fun `Position data class copy`() {
        val position = Position(traderId = "T001", symbol = "AAPL", quantity = 100, avgCost = BigDecimal("150.00"))
        val updated = position.copy(quantity = 150)

        assertEquals("T001", updated.traderId)
        assertEquals(150, updated.quantity)
    }

    @Test
    fun `Side enum has BUY and SELL`() {
        assertEquals(Side.BUY, Side.valueOf("BUY"))
        assertEquals(Side.SELL, Side.valueOf("SELL"))
    }

    @Test
    fun `OrderCancelled data class equality`() {
        val id = UUID.randomUUID()
        val event = OrderCancelled(orderId = id)
        assertEquals(id, event.orderId)
        assertEquals(event, OrderCancelled(orderId = id))
    }
}
