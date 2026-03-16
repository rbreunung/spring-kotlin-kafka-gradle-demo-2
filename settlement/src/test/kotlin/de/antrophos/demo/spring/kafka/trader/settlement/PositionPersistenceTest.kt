package de.antrophos.demo.spring.kafka.trader.settlement

import de.antrophos.demo.spring.kafka.trader.settlement.domain.PositionEntity
import de.antrophos.demo.spring.kafka.trader.settlement.kafka.SettlementEventPublisher
import de.antrophos.demo.spring.kafka.trader.settlement.repository.PositionRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DataJpaTest
@Import(SettlementService::class)
@Suppress("UnusedPrivateProperty")
class PositionPersistenceTest {

    @MockBean
    private lateinit var eventPublisher: SettlementEventPublisher

    @Autowired
    lateinit var settlementService: SettlementService

    @Autowired
    lateinit var positionRepository: PositionRepository

    private val traderId = "trader-001"
    private val symbol = "AAPL"

    private fun trade(price: BigDecimal = BigDecimal("100.00"), orderId: UUID = UUID.randomUUID()) = Trade(
        id = UUID.randomUUID(),
        orderId = orderId,
        executedPrice = price,
        executedAt = Instant.now()
    )

    private fun order(quantity: Int, side: Side, id: UUID = UUID.randomUUID()) = Order(
        id = id,
        traderId = traderId,
        symbol = symbol,
        quantity = quantity,
        side = side
    )

    @BeforeEach
    fun clearPositions() {
        positionRepository.deleteAll()
    }

    @Test
    fun `BUY 100 shares at 100 creates position with correct quantity and avgCost`() {
        settlementService.updatePosition(trade(BigDecimal("100.00")), order(100, Side.BUY))

        val pos = positionRepository.findByTraderIdAndSymbol(traderId, symbol)
        assertNotNull(pos)
        assertEquals(100, pos!!.quantity)
        assertEquals(BigDecimal("100.00"), pos.avgCost.setScale(2))
    }

    @Test
    fun `BUY 100 at 100 then BUY 50 at 120 gives weighted avgCost`() {
        settlementService.updatePosition(trade(BigDecimal("100.00")), order(100, Side.BUY))
        settlementService.updatePosition(trade(BigDecimal("120.00")), order(50, Side.BUY))

        val pos = positionRepository.findByTraderIdAndSymbol(traderId, symbol)!!
        assertEquals(150, pos.quantity)
        assertEquals(BigDecimal("106.67"), pos.avgCost.setScale(2, java.math.RoundingMode.HALF_UP))
    }

    @Test
    fun `SELL 30 from position of 150 reduces quantity, avgCost unchanged`() {
        settlementService.updatePosition(trade(BigDecimal("100.00")), order(100, Side.BUY))
        settlementService.updatePosition(trade(BigDecimal("120.00")), order(50, Side.BUY))
        settlementService.updatePosition(trade(BigDecimal("90.00")), order(30, Side.SELL))

        val pos = positionRepository.findByTraderIdAndSymbol(traderId, symbol)!!
        assertEquals(120, pos.quantity)
        assertEquals(BigDecimal("106.67"), pos.avgCost.setScale(2, java.math.RoundingMode.HALF_UP))
    }

    @Test
    fun `SELL with no existing position creates short position`() {
        settlementService.updatePosition(trade(BigDecimal("90.00")), order(30, Side.SELL))

        val pos = positionRepository.findByTraderIdAndSymbol(traderId, symbol)!!
        assertEquals(-30, pos.quantity)
    }
}
