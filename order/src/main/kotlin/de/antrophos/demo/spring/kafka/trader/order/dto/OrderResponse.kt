package de.antrophos.demo.spring.kafka.trader.order.dto

import de.antrophos.demo.spring.kafka.trader.order.domain.OrderEntity
import java.time.Instant
import java.util.UUID

data class OrderResponse(
    val id: UUID,
    val traderId: String,
    val symbol: String,
    val quantity: Int,
    val side: String,
    val status: String,
    val tradeId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(entity: OrderEntity) = OrderResponse(
            id = entity.id,
            traderId = entity.traderId,
            symbol = entity.symbol,
            quantity = entity.quantity,
            side = entity.side,
            status = entity.status,
            tradeId = entity.tradeId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
