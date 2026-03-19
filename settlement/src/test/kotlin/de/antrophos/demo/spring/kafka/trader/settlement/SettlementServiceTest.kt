package de.antrophos.demo.spring.kafka.trader.settlement

import de.antrophos.demo.spring.kafka.trader.settlement.domain.PositionEntity
import de.antrophos.demo.spring.kafka.trader.settlement.kafka.SettlementEventPublisher
import de.antrophos.demo.spring.kafka.trader.settlement.repository.PositionRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class SettlementServiceTest {

    private val positionRepository: PositionRepository = mock()
    private val eventPublisher: SettlementEventPublisher = mock()

    private val testOrder = Order(
        id = UUID.randomUUID(),
        traderId = "trader-001",
        symbol = "AAPL",
        quantity = 10,
        side = Side.BUY
    )
    private val testTrade = Trade(
        id = UUID.randomUUID(),
        orderId = testOrder.id,
        executedPrice = BigDecimal("150.00"),
        executedAt = Instant.now()
    )

    @Test
    fun `settle increments settlement attempts total with outcome success on success`() {
        val registry = SimpleMeterRegistry()
        val service = SettlementService(positionRepository, eventPublisher, 0.0, 0L, registry)
        whenever(positionRepository.findByTraderIdAndSymbol(any(), any())).thenReturn(null)
        doAnswer { it.arguments[0] as PositionEntity }.whenever(positionRepository).save(any())

        service.settle(testTrade, testOrder)

        val counter = registry.find("settlement.attempts.total").tag("outcome", "success").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }
}
