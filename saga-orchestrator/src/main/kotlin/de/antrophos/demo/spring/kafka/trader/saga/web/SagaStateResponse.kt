package de.antrophos.demo.spring.kafka.trader.saga.web

import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import java.time.Instant
import java.util.UUID

data class SagaStateResponse(
    val orderId: UUID,
    val step: String,
    val tradeId: UUID?,
    val updatedAt: Instant
) {
    companion object {
        fun from(entity: SagaStateEntity) = SagaStateResponse(
            orderId = entity.orderId,
            step = entity.step,
            tradeId = entity.tradeId,
            updatedAt = entity.updatedAt
        )
    }
}
