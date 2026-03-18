package de.antrophos.demo.spring.kafka.trader.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStep
import de.antrophos.demo.spring.kafka.trader.saga.kafka.SagaEventPublisher
import de.antrophos.demo.spring.kafka.trader.saga.repository.SagaStateRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.events.CompensationRequested
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderCancelled
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderPlaced
import de.antrophos.demo.spring.kafka.trader.shared.events.PositionSettled
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskApproved
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeExecuted
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeVoided
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SagaOrchestrator(
    private val repository: SagaStateRepository,
    private val publisher: SagaEventPublisher,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(SagaOrchestrator::class.java)

    @Transactional
    fun onOrderPlaced(event: OrderPlaced) {
        repository.save(
            SagaStateEntity(
                orderId = event.order.id,
                step = SagaStep.RISK_REQUESTED.name,
                orderJson = objectMapper.writeValueAsString(event.order),
                updatedAt = Instant.now()
            )
        )
        publisher.publishRiskCheckRequested(event.order)
        log.info("Saga started for orderId={}", event.order.id)
    }

    @Transactional
    fun onOrderCancelled(event: OrderCancelled) {
        val entity = repository.findById(event.orderId).orElse(null)
        if (entity == null) {
            log.warn("onOrderCancelled: no saga found for orderId={}, skipping", event.orderId)
            return
        }
        if (entity.step != SagaStep.RISK_REQUESTED.name) {
            log.warn("onOrderCancelled: saga for orderId={} is in step={}, cannot cancel", event.orderId, entity.step)
            return
        }
        repository.deleteById(event.orderId)
        log.info("Saga cancelled for orderId={}", event.orderId)
    }

    @Transactional
    fun onRiskApproved(event: RiskApproved) {
        val entity = findOrWarn(event.orderId, "onRiskApproved") ?: return
        if (isTerminalOrWarn(entity, "onRiskApproved")) return
        val order = objectMapper.readValue<Order>(entity.orderJson)
        val approved = repository.save(entity.copy(step = SagaStep.RISK_APPROVED.name, updatedAt = Instant.now()))
        repository.save(approved.copy(step = SagaStep.EXECUTION_REQUESTED.name, updatedAt = Instant.now()))
        publisher.publishExecutionRequested(order)
        log.info("Risk approved for orderId={}, transitioning RISK_APPROVED → EXECUTION_REQUESTED", event.orderId)
    }

    @Transactional
    fun onRiskRejected(event: RiskRejected) {
        val entity = findOrWarn(event.orderId, "onRiskRejected") ?: return
        if (isTerminalOrWarn(entity, "onRiskRejected")) return
        repository.save(entity.copy(step = SagaStep.RISK_REJECTED.name, updatedAt = Instant.now()))
        log.warn("Risk rejected for orderId={}, reason={}", event.orderId, event.reason)
    }

    @Transactional
    fun onTradeExecuted(event: TradeExecuted) {
        val orderId = event.trade.orderId
        val entity = findOrWarn(orderId, "onTradeExecuted") ?: return
        if (isTerminalOrWarn(entity, "onTradeExecuted")) return
        val order = objectMapper.readValue<Order>(entity.orderJson)
        val complete = repository.save(
            entity.copy(
                step = SagaStep.EXECUTION_COMPLETE.name,
                tradeId = event.trade.id,
                updatedAt = Instant.now()
            )
        )
        repository.save(complete.copy(step = SagaStep.SETTLEMENT_REQUESTED.name, updatedAt = Instant.now()))
        publisher.publishSettlementRequested(event.trade, order)
        log.info("Trade executed for orderId={}, tradeId={}, transitioning EXECUTION_COMPLETE → SETTLEMENT_REQUESTED", orderId, event.trade.id)
    }

    @Transactional
    fun onPositionSettled(event: PositionSettled) {
        val orderId = resolveOrderIdByTradeId(event.tradeId, "onPositionSettled") ?: return
        val entity = findOrWarn(orderId, "onPositionSettled") ?: return
        if (isTerminalOrWarn(entity, "onPositionSettled")) return
        if (entity.step != SagaStep.SETTLEMENT_REQUESTED.name) {
            log.warn("onPositionSettled: expected SETTLEMENT_REQUESTED, got {}, skipping", entity.step)
            return
        }
        val order = objectMapper.readValue<Order>(entity.orderJson)
        repository.save(entity.copy(step = SagaStep.SETTLED.name, updatedAt = Instant.now()))
        publisher.publishNotificationRequested(
            traderId = order.traderId,
            orderId = orderId,
            message = "Order $orderId settled successfully"
        )
        log.info("Position settled for orderId={}", orderId)
    }

    @Transactional
    fun onSettlementFailed(event: SettlementFailed) {
        val orderId = resolveOrderIdByTradeId(event.tradeId, "onSettlementFailed") ?: return
        val entity = findOrWarn(orderId, "onSettlementFailed") ?: return
        if (isTerminalOrWarn(entity, "onSettlementFailed")) return
        if (entity.step != SagaStep.SETTLEMENT_REQUESTED.name) {
            log.warn("onSettlementFailed: expected SETTLEMENT_REQUESTED, got {}, skipping", entity.step)
            return
        }
        val tradeId = entity.tradeId
        if (tradeId == null) {
            log.error("onSettlementFailed: tradeId is null for orderId={}, cannot compensate", orderId)
            return
        }
        val afterFailed = repository.save(entity.copy(step = SagaStep.SETTLEMENT_FAILED.name, updatedAt = Instant.now()))
        repository.save(afterFailed.copy(step = SagaStep.COMPENSATION_REQUESTED.name, updatedAt = Instant.now()))
        publisher.publishCompensationRequested(orderId, tradeId, event.reason)
        log.warn("Settlement failed for orderId={}, reason={}, compensation requested", orderId, event.reason)
    }

    @Transactional
    fun onTradeVoided(event: TradeVoided) {
        val orderId = resolveOrderIdByTradeId(event.tradeId, "onTradeVoided") ?: return
        val entity = findOrWarn(orderId, "onTradeVoided") ?: return
        if (isTerminalOrWarn(entity, "onTradeVoided")) return
        if (entity.step != SagaStep.COMPENSATION_REQUESTED.name) {
            log.warn("onTradeVoided: expected COMPENSATION_REQUESTED, got {}, skipping", entity.step)
            return
        }
        repository.save(entity.copy(step = SagaStep.COMPENSATION_COMPLETE.name, updatedAt = Instant.now()))
        log.info("Trade voided for orderId={}, compensation complete", orderId)
    }

    // --- helpers ---

    private fun findOrWarn(orderId: UUID, caller: String): SagaStateEntity? {
        val entity = repository.findById(orderId).orElse(null)
        if (entity == null) log.warn("{}: no saga found for orderId={}, skipping", caller, orderId)
        return entity
    }

    private fun isTerminalOrWarn(entity: SagaStateEntity, caller: String): Boolean {
        val step = SagaStep.valueOf(entity.step)
        return if (step.isTerminal) {
            log.warn("{}: saga for orderId={} is already in terminal step={}, skipping", caller, entity.orderId, entity.step)
            true
        } else false
    }

    private fun resolveOrderIdByTradeId(tradeId: UUID, caller: String): UUID? {
        val entity = repository.findByTradeId(tradeId)
        if (entity == null) log.warn("{}: no saga found for tradeId={}, skipping", caller, tradeId)
        return entity?.orderId
    }
}
