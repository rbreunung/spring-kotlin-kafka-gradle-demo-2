package de.antrophos.demo.spring.kafka.trader.order.repository

import de.antrophos.demo.spring.kafka.trader.order.domain.OrderEntity
import de.antrophos.demo.spring.kafka.trader.order.domain.OrderStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    lateinit var repo: OrderRepository

    private fun anOrder(
        traderId: String = "T1",
        status: String = OrderStatus.PENDING.name,
        tradeId: UUID? = null
    ) = OrderEntity(
        id = UUID.randomUUID(),
        traderId = traderId,
        symbol = "AAPL",
        quantity = 10,
        side = "BUY",
        status = status,
        tradeId = tradeId,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `save and findById`() {
        val entity = anOrder()
        repo.save(entity)
        val found = repo.findById(entity.id).orElse(null)
        assertNotNull(found)
        assertEquals(entity.id, found.id)
        assertEquals(OrderStatus.PENDING.name, found.status)
    }

    @Test
    fun `findAllByTraderId returns matching orders`() {
        repo.save(anOrder(traderId = "alice"))
        repo.save(anOrder(traderId = "bob"))
        repo.save(anOrder(traderId = "alice"))
        val result = repo.findAllByTraderId("alice")
        assertEquals(2, result.size)
        result.forEach { assertEquals("alice", it.traderId) }
    }

    @Test
    fun `findAllByStatus returns matching orders`() {
        repo.save(anOrder(status = OrderStatus.PENDING.name))
        repo.save(anOrder(status = OrderStatus.EXECUTED.name))
        repo.save(anOrder(status = OrderStatus.PENDING.name))
        val result = repo.findAllByStatus(OrderStatus.PENDING.name)
        assertEquals(2, result.size)
    }

    @Test
    fun `findAllByTraderIdAndStatus returns matching orders`() {
        repo.save(anOrder(traderId = "alice", status = OrderStatus.PENDING.name))
        repo.save(anOrder(traderId = "alice", status = OrderStatus.EXECUTED.name))
        repo.save(anOrder(traderId = "bob", status = OrderStatus.PENDING.name))
        val result = repo.findAllByTraderIdAndStatus("alice", OrderStatus.PENDING.name)
        assertEquals(1, result.size)
        assertEquals("alice", result[0].traderId)
        assertEquals(OrderStatus.PENDING.name, result[0].status)
    }

    @Test
    fun `findByTradeId returns order with matching tradeId`() {
        val tradeId = UUID.randomUUID()
        repo.save(anOrder(tradeId = tradeId))
        repo.save(anOrder())
        val found = repo.findByTradeId(tradeId)
        assertNotNull(found)
        assertEquals(tradeId, found.tradeId)
    }

    @Test
    fun `findByTradeId returns null when not found`() {
        assertNull(repo.findByTradeId(UUID.randomUUID()))
    }

    @Test
    fun `unrecognised status returns empty list`() {
        repo.save(anOrder())
        val result = repo.findAllByStatus("GARBAGE")
        assertEquals(0, result.size)
    }
}
