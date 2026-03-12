package de.antrophos.demo.spring.kafka.trader.shared.domain

import java.util.UUID

data class Order(
    val id: UUID,
    val traderId: String,
    val symbol: String,
    val quantity: Int,
    val side: Side
)
