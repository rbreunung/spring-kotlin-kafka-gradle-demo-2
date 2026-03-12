package de.antrophos.demo.spring.kafka.trader.shared.domain

import java.math.BigDecimal

data class Position(
    val traderId: String,
    val symbol: String,
    val quantity: Int,
    val avgCost: BigDecimal
)
