package de.antrophos.demo.spring.kafka.trader.order.service

import de.antrophos.demo.spring.kafka.trader.order.dto.OrderResponse
import de.antrophos.demo.spring.kafka.trader.order.exception.OrderNotFoundException
import de.antrophos.demo.spring.kafka.trader.order.repository.OrderRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderQueryService(private val orderRepository: OrderRepository) {

    fun findById(id: UUID): OrderResponse =
        orderRepository.findById(id)
            .map { OrderResponse.from(it) }
            .orElseThrow { OrderNotFoundException(id) }

    fun findAll(traderId: String?, status: String?): List<OrderResponse> {
        val entities = when {
            traderId != null && status != null -> orderRepository.findAllByTraderIdAndStatus(traderId, status)
            traderId != null -> orderRepository.findAllByTraderId(traderId)
            status != null -> orderRepository.findAllByStatus(status)
            else -> orderRepository.findAll()
        }
        return entities.map { OrderResponse.from(it) }
    }
}
