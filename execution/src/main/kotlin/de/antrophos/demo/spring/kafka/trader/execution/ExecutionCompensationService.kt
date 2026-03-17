package de.antrophos.demo.spring.kafka.trader.execution

import de.antrophos.demo.spring.kafka.trader.execution.domain.TradeStatus
import de.antrophos.demo.spring.kafka.trader.execution.kafka.ExecutionEventPublisher
import de.antrophos.demo.spring.kafka.trader.execution.repository.TradeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ExecutionCompensationService(
    private val tradeRepository: TradeRepository,
    private val publisher: ExecutionEventPublisher
) {
    private val log = LoggerFactory.getLogger(ExecutionCompensationService::class.java)

    fun voidTrade(tradeId: UUID, orderId: UUID) {
        val entity = tradeRepository.findById(tradeId).orElse(null)
        if (entity != null) {
            entity.status = TradeStatus.VOIDED.name
            tradeRepository.save(entity)
        } else {
            log.warn("voidTrade: no TradeEntity found for tradeId={}, publishing TradeVoided anyway", tradeId)
        }
        publisher.publishTradeVoided(tradeId, orderId)
    }
}
