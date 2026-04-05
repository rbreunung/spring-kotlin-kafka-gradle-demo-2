package de.antrophos.demo.spring.kafka.trader.saga.kafka

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade
import de.antrophos.demo.spring.kafka.trader.shared.events.CompensationRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.ExecutionRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.NotificationRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderCancellationComplete
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskCheckRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementRequested
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SagaEventPublisher(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun publishRiskCheckRequested(order: Order) {
        kafkaTemplate.send("risk-checks", order.id.toString(), RiskCheckRequested(order))
    }

    fun publishExecutionRequested(order: Order) {
        kafkaTemplate.send("execution-requests", order.id.toString(), ExecutionRequested(order))
    }

    fun publishSettlementRequested(trade: Trade, order: Order) {
        kafkaTemplate.send("settlement-requests", trade.orderId.toString(), SettlementRequested(trade, order))
    }

    fun publishNotificationRequested(traderId: String, orderId: UUID, message: String) {
        kafkaTemplate.send("notifications", orderId.toString(), NotificationRequested(traderId, orderId, message))
    }

    fun publishCompensationRequested(orderId: UUID, tradeId: UUID, reason: String) {
        kafkaTemplate.send("compensation-requests", orderId.toString(), CompensationRequested(orderId, tradeId, reason))
    }

    fun publishCancellationComplete(orderId: UUID) {
        kafkaTemplate.send("cancellation-results", orderId.toString(), OrderCancellationComplete(orderId))
    }
}
