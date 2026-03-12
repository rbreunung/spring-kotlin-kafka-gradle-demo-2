package de.antrophos.demo.spring.kafka.trader.order.exception

import java.util.UUID

class OrderNotCancellableException(id: UUID, status: String) :
    RuntimeException("Order $id cannot be cancelled: current status is $status")
