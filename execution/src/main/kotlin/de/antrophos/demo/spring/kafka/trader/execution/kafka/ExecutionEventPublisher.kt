package de.antrophos.demo.spring.kafka.trader.execution.kafka

import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeExecuted
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ExecutionEventPublisher(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun publishTradeExecuted(trade: Trade) {
        kafkaTemplate.send("executions", trade.orderId.toString(), TradeExecuted(trade))
    }
}
