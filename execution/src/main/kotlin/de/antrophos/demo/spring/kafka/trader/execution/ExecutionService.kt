package de.antrophos.demo.spring.kafka.trader.execution

import de.antrophos.demo.spring.kafka.trader.execution.kafka.ExecutionEventPublisher
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ExecutionService(private val publisher: ExecutionEventPublisher) {

    fun execute(order: Order) {
        val trade = Trade(
            id = UUID.randomUUID(),
            orderId = order.id,
            executedPrice = BigDecimal.ZERO,
            executedAt = Instant.now()
        )
        publisher.publishTradeExecuted(trade)
    }
}
