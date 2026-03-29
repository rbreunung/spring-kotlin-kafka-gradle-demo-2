package de.antrophos.demo.spring.kafka.trader.shared.events

import java.util.UUID

data class SettlementFailed(val tradeId: UUID, val orderId: UUID, val reason: String)
