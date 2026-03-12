package de.antrophos.demo.spring.kafka.trader.order.exception

import java.util.UUID

class OrderNotFoundException(id: UUID) : RuntimeException("Order not found: $id")
