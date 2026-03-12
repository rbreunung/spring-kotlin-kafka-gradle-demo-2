package de.antrophos.demo.spring.kafka.trader.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    @Column(nullable = false)
    val id: UUID,

    @Column(nullable = false)
    val traderId: String,

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val side: String,

    @Column(nullable = false)
    var status: String,

    @Column(nullable = true)
    var tradeId: UUID? = null,

    @Column(nullable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant
)
