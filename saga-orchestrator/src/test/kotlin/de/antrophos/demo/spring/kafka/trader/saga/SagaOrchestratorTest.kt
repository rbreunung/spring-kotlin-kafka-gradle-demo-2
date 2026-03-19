package de.antrophos.demo.spring.kafka.trader.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStep
import de.antrophos.demo.spring.kafka.trader.saga.kafka.SagaEventPublisher
import de.antrophos.demo.spring.kafka.trader.saga.repository.SagaStateRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Position
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import de.antrophos.demo.spring.kafka.trader.shared.events.PositionSettled
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
import de.antrophos.demo.spring.kafka.trader.shared.events.TradeVoided
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SagaOrchestratorTest {

    @Mock
    lateinit var repository: SagaStateRepository

    @Mock
    lateinit var publisher: SagaEventPublisher

    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    private val orderId: UUID = UUID.randomUUID()
    private val tradeId: UUID = UUID.randomUUID()

    private val order: Order = Order(
        id = orderId,
        traderId = "T1",
        symbol = "AAPL",
        quantity = 100,
        side = Side.BUY
    )

    private val testPosition: Position = Position(
        traderId = "T1",
        symbol = "AAPL",
        quantity = 100,
        avgCost = BigDecimal("150.00")
    )

    @Test
    fun `onPositionSettled records saga duration seconds timer with outcome settled`() {
        val registry = SimpleMeterRegistry()
        val orchestrator = SagaOrchestrator(repository, publisher, objectMapper, registry)

        val sagaState = SagaStateEntity(
            orderId = orderId,
            step = SagaStep.SETTLEMENT_REQUESTED.name,
            tradeId = tradeId,
            updatedAt = Instant.now(),
            orderJson = objectMapper.writeValueAsString(order),
            startedAt = Instant.now().minusSeconds(5)
        )
        `when`(repository.findByTradeId(tradeId)).thenReturn(sagaState)
        `when`(repository.findById(orderId)).thenReturn(Optional.of(sagaState))
        `when`(repository.save(org.mockito.ArgumentMatchers.any(SagaStateEntity::class.java)))
            .thenAnswer { it.arguments[0] }

        orchestrator.onPositionSettled(PositionSettled(tradeId = tradeId, position = testPosition))

        val timer = registry.find("saga.duration.seconds").tag("outcome", "settled").timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1L)
    }

    @Test
    fun `onRiskRejected records saga duration seconds timer with outcome risk rejected`() {
        val registry = SimpleMeterRegistry()
        val orchestrator = SagaOrchestrator(repository, publisher, objectMapper, registry)

        val sagaState = SagaStateEntity(
            orderId = orderId,
            step = SagaStep.RISK_REQUESTED.name,
            tradeId = null,
            updatedAt = Instant.now(),
            orderJson = objectMapper.writeValueAsString(order),
            startedAt = Instant.now().minusSeconds(3)
        )
        `when`(repository.findById(orderId)).thenReturn(Optional.of(sagaState))
        `when`(repository.save(org.mockito.ArgumentMatchers.any(SagaStateEntity::class.java)))
            .thenAnswer { it.arguments[0] }

        orchestrator.onRiskRejected(RiskRejected(orderId = orderId, reason = "Exposure limit exceeded"))

        val timer = registry.find("saga.duration.seconds").tag("outcome", "risk_rejected").timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1L)
    }

    @Test
    fun `onTradeVoided records saga duration seconds timer with outcome compensation complete`() {
        val registry = SimpleMeterRegistry()
        val orchestrator = SagaOrchestrator(repository, publisher, objectMapper, registry)

        val sagaState = SagaStateEntity(
            orderId = orderId,
            step = SagaStep.COMPENSATION_REQUESTED.name,
            tradeId = tradeId,
            updatedAt = Instant.now(),
            orderJson = objectMapper.writeValueAsString(order),
            startedAt = Instant.now().minusSeconds(10)
        )
        `when`(repository.findByTradeId(tradeId)).thenReturn(sagaState)
        `when`(repository.findById(orderId)).thenReturn(Optional.of(sagaState))
        `when`(repository.save(org.mockito.ArgumentMatchers.any(SagaStateEntity::class.java)))
            .thenAnswer { it.arguments[0] }

        orchestrator.onTradeVoided(TradeVoided(tradeId = tradeId, orderId = orderId))

        val timer = registry.find("saga.duration.seconds").tag("outcome", "compensation_complete").timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1L)
    }
}
