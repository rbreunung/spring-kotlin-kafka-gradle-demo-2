package de.antrophos.demo.spring.kafka.trader.shared.events

import java.util.UUID

data class OrderCancellationComplete(val orderId: UUID)
