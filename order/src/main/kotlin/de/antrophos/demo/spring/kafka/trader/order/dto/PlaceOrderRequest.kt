package de.antrophos.demo.spring.kafka.trader.order.dto

import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class PlaceOrderRequest(
    @field:NotBlank val traderId: String,
    @field:NotBlank val symbol: String,
    @field:Min(1) val quantity: Int,
    val side: Side
)
