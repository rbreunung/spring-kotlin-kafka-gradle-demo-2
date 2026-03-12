package de.antrophos.demo.spring.kafka.trader.shared.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Trade(
    val id: UUID,
    val orderId: UUID,
    val executedPrice: BigDecimal,
    val executedAt: Instant
)
