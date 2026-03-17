package de.antrophos.demo.spring.kafka.trader.shared.events

import java.util.UUID

data class CompensationRequested(val orderId: UUID, val tradeId: UUID, val reason: String)
