package de.antrophos.demo.spring.kafka.trader.shared.events

import de.antrophos.demo.spring.kafka.trader.shared.domain.Trade

data class TradeExecuted(val trade: Trade)
