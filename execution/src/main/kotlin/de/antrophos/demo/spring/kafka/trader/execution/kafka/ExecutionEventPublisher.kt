package de.antrophos.demo.spring.kafka.trader.execution.kafka

import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeExecuted
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeVoided
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ExecutionEventPublisher(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun publishTradeExecuted(trade: Trade) {
        kafkaTemplate.send("executions", trade.orderId.toString(), TradeExecuted(trade))
    }

    fun publishTradeVoided(tradeId: UUID, orderId: UUID) {
        kafkaTemplate.send("compensation-results", tradeId.toString(), TradeVoided(tradeId, orderId))
    }
}
