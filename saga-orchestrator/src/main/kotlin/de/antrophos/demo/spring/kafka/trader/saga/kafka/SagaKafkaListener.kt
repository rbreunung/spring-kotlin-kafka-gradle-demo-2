package de.antrophos.demo.spring.kafka.trader.saga.kafka

import de.antrophos.demo.spring.kafka.trader.saga.SagaOrchestrator
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderCancelled
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderPlaced
import de.antrophos.demo.spring.kafka.trader.shared.events.PositionSettled
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskApproved
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeExecuted
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class SagaKafkaListener(private val orchestrator: SagaOrchestrator) {

    private val log = LoggerFactory.getLogger(SagaKafkaListener::class.java)

    @KafkaListener(topics = ["orders"], groupId = "saga-orchestrator")
    fun onOrder(record: ConsumerRecord<String, Any>) {
        when (val event = record.value()) {
            is OrderPlaced -> orchestrator.onOrderPlaced(event)
            is OrderCancelled -> orchestrator.onOrderCancelled(event)
            else -> log.warn("onOrder: unexpected event type {}", event?.javaClass?.simpleName)
        }
    }

    @KafkaListener(topics = ["risk-results"], groupId = "saga-orchestrator")
    fun onRiskResult(record: ConsumerRecord<String, Any>) {
        when (val event = record.value()) {
            is RiskApproved -> orchestrator.onRiskApproved(event)
            is RiskRejected -> orchestrator.onRiskRejected(event)
            else -> log.warn("onRiskResult: unexpected event type {}", event?.javaClass?.simpleName)
        }
    }

    @KafkaListener(topics = ["executions"], groupId = "saga-orchestrator")
    fun onExecution(event: TradeExecuted) {
        orchestrator.onTradeExecuted(event)
    }

    @KafkaListener(topics = ["settlements"], groupId = "saga-orchestrator")
    fun onSettlement(record: ConsumerRecord<String, Any>) {
        when (val event = record.value()) {
            is PositionSettled -> orchestrator.onPositionSettled(event)
            is SettlementFailed -> orchestrator.onSettlementFailed(event)
            else -> log.warn("onSettlement: unexpected event type {}", event?.javaClass?.simpleName)
        }
    }
}
