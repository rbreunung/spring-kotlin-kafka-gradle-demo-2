package de.antrophos.demo.spring.kafka.trader.saga.repository

import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStep
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DataJpaTest
class SagaStateRepositoryTest {

    @Autowired
    lateinit var repo: SagaStateRepository

    private fun aSagaState(
        step: SagaStep = SagaStep.RISK_REQUESTED,
        tradeId: UUID? = null
    ) = SagaStateEntity(
        orderId = UUID.randomUUID(),
        step = step.name,
        tradeId = tradeId,
        updatedAt = Instant.now()
    )

    @Test
    fun `save and findById returns correct orderId and step`() {
        val entity = aSagaState(step = SagaStep.RISK_REQUESTED)
        repo.save(entity)
        val found = repo.findById(entity.orderId).orElse(null)
        assertNotNull(found)
        assertEquals(entity.orderId, found.orderId)
        assertEquals(SagaStep.RISK_REQUESTED.name, found.step)
    }

    @Test
    fun `step is stored as string RISK_APPROVED`() {
        val entity = aSagaState(step = SagaStep.RISK_APPROVED)
        repo.save(entity)
        val found = repo.findById(entity.orderId).orElseThrow()
        assertEquals("RISK_APPROVED", found.step)
    }

    @Test
    fun `findAllByOrderByUpdatedAtDesc returns newest first`() {
        val older = aSagaState().copy(updatedAt = Instant.now().minusSeconds(10))
        val newer = aSagaState().copy(updatedAt = Instant.now())
        repo.save(older)
        repo.save(newer)
        val results = repo.findAllByOrderByUpdatedAtDesc()
        assertEquals(2, results.size)
        assertEquals(newer.orderId, results[0].orderId)
    }
}
