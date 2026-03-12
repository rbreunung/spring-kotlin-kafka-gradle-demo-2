package de.antrophos.demo.spring.kafka.trader.shared.events

import java.util.UUID

data class TraderNotified(val traderId: String, val orderId: UUID, val message: String)
