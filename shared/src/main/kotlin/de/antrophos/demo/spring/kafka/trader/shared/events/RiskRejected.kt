package de.antrophos.demo.spring.kafka.trader.shared.events

import java.util.UUID

data class RiskRejected(val orderId: UUID, val reason: String)
