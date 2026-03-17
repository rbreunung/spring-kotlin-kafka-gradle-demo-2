package de.antrophos.demo.spring.kafka.trader.shared.events

import java.util.UUID

data class TradeVoided(val tradeId: UUID, val orderId: UUID)
