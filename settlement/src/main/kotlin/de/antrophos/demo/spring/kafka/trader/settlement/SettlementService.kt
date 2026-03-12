package de.antrophos.demo.spring.kafka.trader.settlement

import de.antrophos.demo.spring.kafka.trader.shared.domain.Position
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class SettlementService {

    fun settle(trade: Trade): Position =
        Position(traderId = "stub", symbol = "stub", quantity = trade.executedPrice.toInt(), avgCost = BigDecimal.ZERO)
}
