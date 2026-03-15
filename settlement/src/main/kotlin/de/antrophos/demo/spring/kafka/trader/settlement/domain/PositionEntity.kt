package de.antrophos.demo.spring.kafka.trader.settlement.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "positions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["trader_id", "symbol"])]
)
data class PositionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @Column(name = "trader_id", nullable = false) val traderId: String,
    @Column(nullable = false) val symbol: String,
    @Column(nullable = false) var quantity: Int,
    @Column(nullable = false) var avgCost: BigDecimal,
    @Column(nullable = false) var updatedAt: Instant
)
