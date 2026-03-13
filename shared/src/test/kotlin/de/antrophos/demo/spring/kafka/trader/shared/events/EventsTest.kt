package de.antrophos.demo.spring.kafka.trader.shared.events

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class EventsTest {

    @Test
    fun `OrderCancelled data class equality`() {
        val id = UUID.randomUUID()
        val event = OrderCancelled(orderId = id)
        assertEquals(id, event.orderId)
        assertEquals(event, OrderCancelled(orderId = id))
    }

    @Test
    fun `RiskCheckRequested data class equality`() {
        val order = Order(
            id = UUID.randomUUID(),
            traderId = "T1",
            symbol = "AAPL",
            quantity = 100,
            side = Side.BUY
        )
        val event = RiskCheckRequested(order = order)
        assertEquals(order, event.order)
        assertEquals(event, RiskCheckRequested(order = order))
    }
}
