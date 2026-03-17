package de.antrophos.demo.spring.kafka.trader.execution.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "trades")
data class TradeEntity(
    @Id
    val id: UUID,

    @Column(nullable = false)
    val orderId: UUID,

    @Column(nullable = false)
    val executedPrice: BigDecimal,

    @Column(nullable = false)
    val executedAt: Instant,

    @Column(nullable = false)
    var status: String = TradeStatus.EXECUTED.name
)
