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
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class SagaOrchestrator(
    private val repository: SagaStateRepository,
    private val publisher: SagaEventPublisher,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry
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
        val currentStep = SagaStep.valueOf(entity.step)
        
        when (currentStep) {
            // Allow cancellation at all non-terminal steps except those that have already executed trades
            SagaStep.RISK_REQUESTED, SagaStep.RISK_APPROVED, SagaStep.EXECUTION_REQUESTED -> {
                // Immediate cancellation - clean abort
                log.info("Saga cancelled at step {} for orderId={}", currentStep, event.orderId)
                repository.save(entity.copy(
                    step = SagaStep.CANCELLATION_COMPLETE.name,
                    updatedAt = Instant.now()
                ))
                publisher.publishCancellationComplete(event.orderId)
            }
            SagaStep.EXECUTION_COMPLETE -> {
                // Trigger compensation for existing trade
                log.info("Saga cancellation requested at step {} for orderId={}; triggering compensation", currentStep, event.orderId)
                val updatedEntity = repository.save(entity.copy(
                    step = SagaStep.COMPENSATION_REQUESTED.name,
                    updatedAt = Instant.now()
                ))
                if (updatedEntity.tradeId != null) {
                    publisher.publishCompensationRequested(event.orderId, updatedEntity.tradeId, "User-initiated cancellation")
                }
            }
            SagaStep.SETTLEMENT_REQUESTED -> {
                // Cancellation wins - transition to CANCEL_PENDING to wait for settlement outcome
                log.info("Saga cancellation requested at step {} for orderId={}; waiting for settlement result", currentStep, event.orderId)
                repository.save(entity.copy(
                    step = SagaStep.CANCEL_PENDING.name,
                    updatedAt = Instant.now()
                ))
                // Do NOT trigger compensation immediately - wait for settlement outcome
            }
            SagaStep.SETTLED -> {
                // Cancel arrived after full settlement — trigger compensation to void the trade
                log.info("Saga cancellation requested at step {} for orderId={}; triggering post-settlement compensation", currentStep, event.orderId)
                val updatedEntity = repository.save(entity.copy(
                    step = SagaStep.COMPENSATION_REQUESTED.name,
                    updatedAt = Instant.now()
                ))
                if (updatedEntity.tradeId != null) {
                    publisher.publishCompensationRequested(event.orderId, updatedEntity.tradeId, "User-initiated cancellation - order already settled")
                }
            }
            else -> {
                // For other steps (terminal or already compensating), do not cancel
                log.warn("onOrderCancelled: saga for orderId={} is in terminal state {} or already compensating, skipping cancel", event.orderId, currentStep)
            }
        }
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
        recordSagaDuration(entity, "risk_rejected")
        log.warn("Risk rejected for orderId={}, reason={}", event.orderId, event.reason)
    }

    @Transactional
    fun onTradeExecuted(event: TradeExecuted) {
        val orderId = event.trade.orderId
        val entity = findOrWarn(orderId, "onTradeExecuted") ?: return
        if (isTerminalOrWarn(entity, "onTradeExecuted")) return
        val order = objectMapper.readValue<Order>(entity.orderJson)
        repository.save(
            entity.copy(
                step = SagaStep.EXECUTION_COMPLETE.name,
                tradeId = event.trade.id,
                updatedAt = Instant.now()
            )
        )
        // Conditional transition: only advance to SETTLEMENT_REQUESTED if the saga is still at
        // EXECUTION_COMPLETE. A concurrent cancel (onOrderCancelled) may have already moved the
        // saga to COMPENSATION_REQUESTED — in that case we must not overwrite it.
        val updated = repository.transitionStep(
            orderId, SagaStep.EXECUTION_COMPLETE.name, SagaStep.SETTLEMENT_REQUESTED.name, Instant.now()
        )
        if (updated == 0) {
            log.info("onTradeExecuted: saga {} was concurrently modified from EXECUTION_COMPLETE (cancel in progress?), skipping SETTLEMENT_REQUESTED", orderId)
            return
        }
        publisher.publishSettlementRequested(event.trade, order)
        log.info("Trade executed for orderId={}, tradeId={}, transitioning EXECUTION_COMPLETE → SETTLEMENT_REQUESTED", orderId, event.trade.id)
    }

    @Transactional
    fun onPositionSettled(event: PositionSettled) {
        val orderId = resolveOrderIdByTradeId(event.tradeId, "onPositionSettled") ?: return
        val entity = findOrWarn(orderId, "onPositionSettled") ?: return
        if (isTerminalOrWarn(entity, "onPositionSettled")) return
        if (entity.step != SagaStep.SETTLEMENT_REQUESTED.name && entity.step != SagaStep.CANCEL_PENDING.name) {
            log.warn("onPositionSettled: expected SETTLEMENT_REQUESTED or CANCEL_PENDING, got {}, skipping", entity.step)
            return
        }
        val order = objectMapper.readValue<Order>(entity.orderJson)
        if (entity.step == SagaStep.CANCEL_PENDING.name) {
            // Cancellation wins - trigger compensation for the settled position
            log.info("Position settled for orderId={} after cancellation request - triggering compensation", orderId)
            repository.save(entity.copy(step = SagaStep.COMPENSATION_REQUESTED.name, updatedAt = Instant.now()))
            if (entity.tradeId != null) {
                publisher.publishCompensationRequested(orderId, entity.tradeId, "User-initiated cancellation - trade settled despite cancellation")
            }
        } else {
            // Normal settlement
            repository.save(entity.copy(step = SagaStep.SETTLED.name, updatedAt = Instant.now()))
            publisher.publishNotificationRequested(
                traderId = order.traderId,
                orderId = orderId,
                message = "Order $orderId settled successfully"
            )
            recordSagaDuration(entity, "settled")
            log.info("Position settled for orderId={}", orderId)
        }
    }

    @Transactional
    fun onSettlementFailed(event: SettlementFailed) {
        val orderId = resolveOrderIdByTradeId(event.tradeId, "onSettlementFailed") ?: return
        val entity = findOrWarn(orderId, "onSettlementFailed") ?: return
        if (isTerminalOrWarn(entity, "onSettlementFailed")) return
        if (entity.step != SagaStep.SETTLEMENT_REQUESTED.name && entity.step != SagaStep.CANCEL_PENDING.name) {
            log.warn("onSettlementFailed: expected SETTLEMENT_REQUESTED or CANCEL_PENDING, got {}, skipping", entity.step)
            return
        }
        val tradeId = entity.tradeId
        if (tradeId == null) {
            log.error("onSettlementFailed: tradeId is null for orderId={}, cannot compensate", orderId)
            return
        }
        val afterFailed = repository.save(entity.copy(step = SagaStep.SETTLEMENT_FAILED.name, updatedAt = Instant.now()))
        if (entity.step == SagaStep.CANCEL_PENDING.name) {
            // Cancellation wins - trigger compensation as a result of cancellation
            log.info("Settlement failed for orderId={} (after cancellation request) - triggering compensation", orderId)
            repository.save(afterFailed.copy(step = SagaStep.COMPENSATION_REQUESTED.name, updatedAt = Instant.now()))
            publisher.publishCompensationRequested(orderId, tradeId, "User-initiated cancellation - settlement failed")
        } else {
            // Normal settlement failure - regular compensation
            repository.save(afterFailed.copy(step = SagaStep.COMPENSATION_REQUESTED.name, updatedAt = Instant.now()))
            publisher.publishCompensationRequested(orderId, tradeId, event.reason)
            log.warn("Settlement failed for orderId={}, reason={}, compensation requested", orderId, event.reason)
        }
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
        recordSagaDuration(entity, "compensation_complete")
        log.info("Trade voided for orderId={}, compensation complete", orderId)
    }

    // --- metrics ---

    private fun recordSagaDuration(entity: SagaStateEntity, outcome: String) {
        val duration = Duration.between(entity.startedAt, Instant.now())
        meterRegistry.timer("saga.duration.seconds", "outcome", outcome).record(duration)
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
