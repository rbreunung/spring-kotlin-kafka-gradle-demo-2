package de.antrophos.demo.spring.kafka.trader.execution

import de.antrophos.demo.spring.kafka.trader.execution.kafka.ExecutionEventPublisher
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.util.UUID

class ExecutionServiceTest {

    private val publisher = mock<ExecutionEventPublisher>()
    private val basePrice = BigDecimal("100.00")
    private val service = ExecutionService(publisher, basePrice)

    private val order = Order(
        id = UUID.randomUUID(),
        traderId = "trader-1",
        symbol = "AAPL",
        quantity = 100,
        side = Side.BUY
    )

    @Test
    fun `execute produces trade with orderId matching order id`() {
        service.execute(order)

        val captor = argumentCaptor<Trade>()
        verify(publisher).publishTradeExecuted(captor.capture())
        assertEquals(order.id, captor.firstValue.orderId)
    }

    @Test
    fun `execute produces trade with unique non-null id`() {
        service.execute(order)

        val captor = argumentCaptor<Trade>()
        verify(publisher).publishTradeExecuted(captor.capture())
        assertNotNull(captor.firstValue.id)
    }

    @Test
    fun `execute produces trade with executedAt set`() {
        service.execute(order)

        val captor = argumentCaptor<Trade>()
        verify(publisher).publishTradeExecuted(captor.capture())
        assertNotNull(captor.firstValue.executedAt)
    }

    @Test
    fun `execute produces executedPrice within 2 percent of base price across 100 calls`() {
        val lowerBound = basePrice.multiply(BigDecimal("0.98"))
        val upperBound = basePrice.multiply(BigDecimal("1.02"))

        repeat(100) {
            val o = Order(id = UUID.randomUUID(), traderId = "t", symbol = "X", quantity = 1, side = Side.BUY)
            service.execute(o)
        }

        val captor = argumentCaptor<Trade>()
        verify(publisher, times(100)).publishTradeExecuted(captor.capture())

        captor.allValues.forEach { trade ->
            assert(trade.executedPrice >= lowerBound) { "executedPrice ${trade.executedPrice} below $lowerBound" }
            assert(trade.executedPrice <= upperBound) { "executedPrice ${trade.executedPrice} above $upperBound" }
        }
    }
}
