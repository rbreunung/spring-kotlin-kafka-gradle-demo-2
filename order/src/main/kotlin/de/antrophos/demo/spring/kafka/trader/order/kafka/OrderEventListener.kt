package de.antrophos.demo.spring.kafka.trader.order.kafka

import de.antrophos.demo.spring.kafka.trader.order.domain.OrderStatus
import de.antrophos.demo.spring.kafka.trader.order.repository.OrderRepository
import de.antrophos.demo.spring.kafka.trader.order.service.OrderCommandService
import de.antrophos.demo.spring.kafka.trader.shared.events.PositionSettled
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskApproved
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeExecuted
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeVoided
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventListener(
    private val commandService: OrderCommandService,
    private val orderRepository: OrderRepository
) {
    private val log = LoggerFactory.getLogger(OrderEventListener::class.java)

    @KafkaListener(topics = ["risk-results"], groupId = "order-service")
    fun onRiskResult(record: ConsumerRecord<String, Any>) {
        when (val event = record.value()) {
            is RiskApproved -> commandService.applyTransition(
                event.orderId, OrderStatus.RISK_APPROVED, fromStatus = OrderStatus.PENDING
            )
            is RiskRejected -> commandService.applyTransition(
                event.orderId, OrderStatus.RISK_REJECTED, fromStatus = OrderStatus.PENDING
            )
            else -> log.warn("onRiskResult: unexpected event type {}", event?.javaClass?.simpleName)
        }
    }

    @KafkaListener(topics = ["executions"], groupId = "order-service")
    fun onExecution(event: TradeExecuted) {
        commandService.applyTransition(
            event.trade.orderId, OrderStatus.EXECUTED,
            tradeId = event.trade.id, fromStatus = OrderStatus.RISK_APPROVED
        )
    }

    @KafkaListener(topics = ["settlements"], groupId = "order-service")
    fun onSettlement(record: ConsumerRecord<String, Any>) {
        when (val event = record.value()) {
            is PositionSettled -> {
                val entity = orderRepository.findByTradeId(event.tradeId)
                if (entity == null) {
                    log.warn("onSettlement: no order found for tradeId {}, skipping", event.tradeId)
                    return
                }
                commandService.applyTransition(entity.id, OrderStatus.SETTLED, fromStatus = OrderStatus.EXECUTED)
            }
            is SettlementFailed -> {
                val entity = orderRepository.findByTradeId(event.tradeId)
                if (entity == null) {
                    log.warn("onSettlement: no order found for tradeId {}, skipping", event.tradeId)
                    return
                }
                commandService.applyTransition(entity.id, OrderStatus.COMPENSATION_IN_PROGRESS, fromStatus = OrderStatus.EXECUTED)
            }
            else -> log.warn("onSettlement: unexpected event type {}", event?.javaClass?.simpleName)
        }
    }

    @KafkaListener(topics = ["compensation-results"], groupId = "order-service")
    fun onCompensationResult(event: TradeVoided) {
        commandService.applyTransition(event.orderId, OrderStatus.COMPENSATION_COMPLETE, fromStatus = OrderStatus.COMPENSATION_IN_PROGRESS)
    }
}
