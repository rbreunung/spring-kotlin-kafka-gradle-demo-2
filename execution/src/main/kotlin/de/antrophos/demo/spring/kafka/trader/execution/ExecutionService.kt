package de.antrophos.demo.spring.kafka.trader.execution

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ExecutionService {

    fun execute(order: Order): Trade =
        Trade(id = UUID.randomUUID(), orderId = order.id, executedPrice = BigDecimal.ZERO, executedAt = Instant.now())
}
