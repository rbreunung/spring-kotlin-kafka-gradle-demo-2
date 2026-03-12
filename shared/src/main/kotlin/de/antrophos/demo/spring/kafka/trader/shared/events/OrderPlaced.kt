package de.antrophos.demo.spring.kafka.trader.shared.events

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order

data class OrderPlaced(val order: Order)
