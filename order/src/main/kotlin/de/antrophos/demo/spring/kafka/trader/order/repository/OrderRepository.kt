package de.antrophos.demo.spring.kafka.trader.order.repository

import de.antrophos.demo.spring.kafka.trader.order.domain.OrderEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<OrderEntity, UUID> {
    fun findAllByTraderId(traderId: String): List<OrderEntity>
    fun findAllByStatus(status: String): List<OrderEntity>
    fun findAllByTraderIdAndStatus(traderId: String, status: String): List<OrderEntity>
    fun findByTradeId(tradeId: UUID): OrderEntity?
}
