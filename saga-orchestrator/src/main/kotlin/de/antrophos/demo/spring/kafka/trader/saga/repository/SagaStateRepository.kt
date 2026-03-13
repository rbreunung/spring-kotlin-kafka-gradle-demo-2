package de.antrophos.demo.spring.kafka.trader.saga.repository

import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SagaStateRepository : JpaRepository<SagaStateEntity, UUID> {
    fun findAllByOrderByUpdatedAtDesc(): List<SagaStateEntity>
}
