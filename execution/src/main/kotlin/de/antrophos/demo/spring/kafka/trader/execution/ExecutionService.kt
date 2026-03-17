package de.antrophos.demo.spring.kafka.trader.execution

import de.antrophos.demo.spring.kafka.trader.execution.domain.TradeEntity
import de.antrophos.demo.spring.kafka.trader.execution.kafka.ExecutionEventPublisher
import de.antrophos.demo.spring.kafka.trader.execution.repository.TradeRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@Service
class ExecutionService(
    private val publisher: ExecutionEventPublisher,
    private val tradeRepository: TradeRepository,
    @Value("\${execution.base-price}") private val basePrice: BigDecimal
) {

    fun execute(order: Order) {
        val randomFactor = ThreadLocalRandom.current().nextDouble(-0.02, 0.02)
        val executedPrice = basePrice
            .multiply(BigDecimal.ONE.add(BigDecimal(randomFactor)))
            .setScale(2, RoundingMode.HALF_UP)
        val trade = Trade(
            id = UUID.randomUUID(),
            orderId = order.id,
            executedPrice = executedPrice,
            executedAt = Instant.now()
        )
        tradeRepository.save(
            TradeEntity(
                id = trade.id,
                orderId = trade.orderId,
                executedPrice = trade.executedPrice,
                executedAt = trade.executedAt
            )
        )
        publisher.publishTradeExecuted(trade)
    }
}
