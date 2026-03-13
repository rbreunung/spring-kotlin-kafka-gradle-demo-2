package de.antrophos.demo.spring.kafka.trader.shared.events

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
}
