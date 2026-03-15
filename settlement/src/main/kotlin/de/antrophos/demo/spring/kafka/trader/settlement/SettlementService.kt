package de.antrophos.demo.spring.kafka.trader.settlement

import de.antrophos.demo.spring.kafka.trader.settlement.domain.PositionEntity
import de.antrophos.demo.spring.kafka.trader.settlement.exception.SettlementException
import de.antrophos.demo.spring.kafka.trader.settlement.repository.PositionRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Position
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Service
class SettlementService(
    private val positionRepository: PositionRepository,
    @Value("\${settlement.simulate-failure-probability:0.0}") private val failureProbability: Double
) {

    fun settle(trade: Trade, order: Order): Position {
        simulateFailure(trade)
        return updatePosition(trade, order)
    }

    fun updatePosition(trade: Trade, order: Order): Position {
        val existing = positionRepository.findByTraderIdAndSymbol(order.traderId, order.symbol)
        val entity = if (existing != null) {
            applyTrade(existing, trade, order)
        } else {
            createPosition(trade, order)
        }
        val saved = positionRepository.save(entity)
        return Position(
            traderId = saved.traderId,
            symbol = saved.symbol,
            quantity = saved.quantity,
            avgCost = saved.avgCost
        )
    }

    private fun applyTrade(existing: PositionEntity, trade: Trade, order: Order): PositionEntity {
        return when (order.side) {
            Side.BUY -> {
                val newQty = existing.quantity + order.quantity
                val newAvgCost = if (newQty > 0) {
                    (existing.avgCost.multiply(BigDecimal(existing.quantity))
                        .add(trade.executedPrice.multiply(BigDecimal(order.quantity))))
                        .divide(BigDecimal(newQty), 10, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }
                existing.copy(quantity = newQty, avgCost = newAvgCost, updatedAt = Instant.now())
            }
            Side.SELL -> existing.copy(
                quantity = existing.quantity - order.quantity,
                updatedAt = Instant.now()
            )
        }
    }

    private fun createPosition(trade: Trade, order: Order): PositionEntity {
        val (quantity, avgCost) = when (order.side) {
            Side.BUY -> order.quantity to trade.executedPrice
            Side.SELL -> -order.quantity to BigDecimal.ZERO
        }
        return PositionEntity(
            traderId = order.traderId,
            symbol = order.symbol,
            quantity = quantity,
            avgCost = avgCost,
            updatedAt = Instant.now()
        )
    }

    private fun simulateFailure(trade: Trade) {
        if (failureProbability > 0.0 && Math.random() < failureProbability) {
            throw SettlementException("Simulated failure for tradeId=${trade.id}")
        }
    }
}
