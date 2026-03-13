package de.antrophos.demo.spring.kafka.trader.saga.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "saga_states")
data class SagaStateEntity(
    @Id
    @Column(nullable = false)
    val orderId: UUID,

    @Column(nullable = false)
    val step: String,

    @Column(nullable = true)
    val tradeId: UUID? = null,

    @Column(nullable = false)
    val updatedAt: Instant
)
