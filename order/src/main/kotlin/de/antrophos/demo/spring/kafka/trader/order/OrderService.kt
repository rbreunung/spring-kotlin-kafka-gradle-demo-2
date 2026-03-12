package de.antrophos.demo.spring.kafka.trader.order

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import org.springframework.stereotype.Service

@Service
class OrderService {

    fun place(order: Order): Order = order
}
