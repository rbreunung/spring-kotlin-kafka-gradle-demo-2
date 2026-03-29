package de.antrophos.demo.spring.kafka.trader.settlement

import de.antrophos.demo.spring.kafka.trader.settlement.domain.PositionEntity
import de.antrophos.demo.spring.kafka.trader.settlement.exception.SettlementException
import de.antrophos.demo.spring.kafka.trader.settlement.kafka.SettlementEventPublisher
import de.antrophos.demo.spring.kafka.trader.settlement.repository.PositionRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Position
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.retry.annotation.Retry
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Service
class SettlementService(
    private val positionRepository: PositionRepository,
    private val eventPublisher: SettlementEventPublisher,
    @Value("\${settlement.simulate-failure-probability:0.0}") private val failureProbability: Double,
    @Value("\${settlement.artificial-delay-ms:0}") private val artificialDelayMs: Long,
    @Value("\${settlement.always-fail-trader-ids:}") private val alwaysFailTraderIds: String,
    private val meterRegistry: MeterRegistry
) {

    private val alwaysFailSet: Set<String> by lazy {
        alwaysFailTraderIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    @Bulkhead(name = "settlementOperation", fallbackMethod = "settleBulkheadFallback")
    @Retry(name = "settlementOperation", fallbackMethod = "settleFallback")
    fun settle(trade: Trade, order: Order): Position {
        if (artificialDelayMs > 0) Thread.sleep(artificialDelayMs)
        simulateFailure(trade, order)
        val position = updatePosition(trade, order)
        eventPublisher.publishPositionSettled(trade.id, position)
        meterRegistry.counter("settlement.attempts.total", "outcome", "success").increment()
        return position
    }

    @Suppress("unused")
    private fun settleBulkheadFallback(trade: Trade, order: Order, ex: Exception): Position {
        eventPublisher.publishSettlementFailed(trade.id, order.id, "bulkhead-full")
        meterRegistry.counter("settlement.attempts.total", "outcome", "failure").increment()
        throw ex
    }

    @Suppress("unused")
    private fun settleFallback(trade: Trade, order: Order, ex: Exception): Position {
        eventPublisher.publishSettlementFailed(trade.id, order.id, ex.message ?: "unknown error")
        meterRegistry.counter("settlement.attempts.total", "outcome", "failure").increment()
        throw ex
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

    private fun simulateFailure(trade: Trade, order: Order) {
        if (order.traderId in alwaysFailSet) {
            throw SettlementException("Always-fail trader configured: ${order.traderId}")
        }
        if (failureProbability > 0.0 && Math.random() < failureProbability) {
            throw SettlementException("Simulated failure for tradeId=${trade.id}")
        }
    }
}
