package de.antrophos.demo.spring.kafka.trader.shared.events

import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade

data class SettlementRequested(val trade: Trade)
