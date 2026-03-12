package de.antrophos.demo.spring.kafka.trader.order.service

import de.antrophos.demo.spring.kafka.trader.order.domain.OrderEntity
import de.antrophos.demo.spring.kafka.trader.order.domain.OrderStatus
import de.antrophos.demo.spring.kafka.trader.order.dto.OrderResponse
import de.antrophos.demo.spring.kafka.trader.order.dto.PlaceOrderRequest
import de.antrophos.demo.spring.kafka.trader.order.exception.OrderNotFoundException
import de.antrophos.demo.spring.kafka.trader.order.exception.OrderNotCancellableException
import de.antrophos.demo.spring.kafka.trader.order.repository.OrderRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderCancelled
import de.antrophos.demo.spring.kafka.trader.shared.events.OrderPlaced
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OrderCommandService(
    private val orderRepository: OrderRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(OrderCommandService::class.java)

    @Transactional
    fun place(request: PlaceOrderRequest): OrderResponse {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val entity = OrderEntity(
            id = id,
            traderId = request.traderId,
            symbol = request.symbol,
            quantity = request.quantity,
            side = request.side.name,
            status = OrderStatus.PENDING.name,
            createdAt = now,
            updatedAt = now
        )
        orderRepository.save(entity)
        val order = Order(
            id = id,
            traderId = request.traderId,
            symbol = request.symbol,
            quantity = request.quantity,
            side = request.side
        )
        kafkaTemplate.send("orders", id.toString(), OrderPlaced(order))
        return OrderResponse.from(entity)
    }

    @Transactional
    fun cancel(id: UUID) {
        val entity = orderRepository.findById(id)
            .orElseThrow { OrderNotFoundException(id) }
        if (entity.status != OrderStatus.PENDING.name) {
            throw OrderNotCancellableException(id, entity.status)
        }
        entity.status = OrderStatus.CANCELLED.name
        entity.updatedAt = Instant.now()
        orderRepository.save(entity)
        kafkaTemplate.send("orders", id.toString(), OrderCancelled(id))
    }

    @Transactional
    fun applyTransition(orderId: UUID, toStatus: OrderStatus, tradeId: UUID? = null) {
        val entity = orderRepository.findById(orderId).orElse(null)
        if (entity == null) {
            log.warn("applyTransition: order {} not found, skipping", orderId)
            return
        }
        val current = OrderStatus.valueOf(entity.status)
        if (current.isTerminal) {
            log.warn("applyTransition: order {} is terminal ({}), skipping transition to {}", orderId, current, toStatus)
            return
        }
        entity.status = toStatus.name
        entity.updatedAt = Instant.now()
        if (tradeId != null) entity.tradeId = tradeId
        orderRepository.save(entity)
    }
}
