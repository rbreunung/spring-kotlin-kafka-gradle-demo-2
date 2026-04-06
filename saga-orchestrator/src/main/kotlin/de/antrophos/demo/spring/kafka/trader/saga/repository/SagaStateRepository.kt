package de.antrophos.demo.spring.kafka.trader.saga.repository

import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SagaStateRepository : JpaRepository<SagaStateEntity, UUID> {
    fun findAllByOrderByUpdatedAtDesc(): List<SagaStateEntity>
    fun findByTradeId(tradeId: UUID): SagaStateEntity?

    /**
     * Atomically transitions the saga step only if it is currently at [fromStep].
     * Returns 1 if the row was updated, 0 if the step no longer matched (concurrent update).
     */
    @Modifying
    @Query("UPDATE SagaStateEntity s SET s.step = :toStep, s.updatedAt = :now WHERE s.orderId = :orderId AND s.step = :fromStep")
    fun transitionStep(
        @Param("orderId") orderId: UUID,
        @Param("fromStep") fromStep: String,
        @Param("toStep") toStep: String,
        @Param("now") now: Instant
    ): Int
}
