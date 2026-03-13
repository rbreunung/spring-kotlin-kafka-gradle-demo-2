package de.antrophos.demo.spring.kafka.trader.risk

import de.antrophos.demo.spring.kafka.trader.risk.external.RiskEngineException
import de.antrophos.demo.spring.kafka.trader.risk.external.RiskExternalClient
import de.antrophos.demo.spring.kafka.trader.risk.kafka.RiskEventPublisher
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskCheckRequested
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RiskServiceTest {

    @Mock lateinit var publisher: RiskEventPublisher
    @Mock lateinit var externalClient: RiskExternalClient

    private lateinit var service: RiskService

    @BeforeEach
    fun setUp() {
        service = RiskService(publisher, externalClient)
    }

    private fun order(quantity: Int) = Order(
        id = UUID.randomUUID(), traderId = "T1", symbol = "AAPL", quantity = quantity, side = Side.BUY
    )

    @Test
    fun `approve order with quantity within limit`() {
        val order = order(100)
        `when`(externalClient.evaluate(order)).thenReturn(true)

        service.handle(RiskCheckRequested(order))

        verify(publisher, times(1)).publishApproved(order.id)
        verify(publisher, times(0)).publishRejected(order.id, "quantity-exceeds-limit")
    }

    @Test
    fun `approve order at exact quantity boundary 10000`() {
        val order = order(10_000)
        `when`(externalClient.evaluate(order)).thenReturn(true)

        service.handle(RiskCheckRequested(order))

        verify(publisher).publishApproved(order.id)
        verifyNoMoreInteractions(publisher)
    }

    @Test
    fun `reject order exceeding quantity limit without calling external client`() {
        val order = order(10_001)

        service.handle(RiskCheckRequested(order))

        verify(publisher).publishRejected(order.id, "quantity-exceeds-limit")
        verifyNoInteractions(externalClient)
    }

    @Test
    fun `do not publish when external client returns false`() {
        val order = order(100)
        `when`(externalClient.evaluate(order)).thenReturn(false)

        service.handle(RiskCheckRequested(order))

        verify(publisher, never()).publishApproved(order.id)
        verifyNoMoreInteractions(publisher)
    }

    @Test
    fun `publish evaluation-failed when external client throws RiskEngineException`() {
        val order = order(100)
        `when`(externalClient.evaluate(order)).thenThrow(
            RiskEngineException("simulated failure")
        )

        service.handle(RiskCheckRequested(order))

        verify(publisher).publishRejected(order.id, "evaluation-failed")
        verify(publisher, never()).publishApproved(order.id)
    }
}
