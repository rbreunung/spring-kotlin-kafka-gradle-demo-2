package de.antrophos.demo.spring.kafka.trader.order.service

import de.antrophos.demo.spring.kafka.trader.order.domain.OrderEntity
import de.antrophos.demo.spring.kafka.trader.order.domain.OrderStatus
import de.antrophos.demo.spring.kafka.trader.order.exception.OrderNotFoundException
import de.antrophos.demo.spring.kafka.trader.order.repository.OrderRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class OrderQueryServiceTest {

    @Mock lateinit var orderRepository: OrderRepository

    private lateinit var service: OrderQueryService

    @BeforeEach
    fun setUp() {
        service = OrderQueryService(orderRepository)
    }

    private fun anEntity(traderId: String = "T1", status: String = OrderStatus.PENDING.name) = OrderEntity(
        id = UUID.randomUUID(),
        traderId = traderId,
        symbol = "AAPL",
        quantity = 10,
        side = "BUY",
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `findById returns response for known id`() {
        val entity = anEntity()
        `when`(orderRepository.findById(entity.id)).thenReturn(Optional.of(entity))

        val response = service.findById(entity.id)

        assertEquals(entity.id, response.id)
        assertEquals(OrderStatus.PENDING.name, response.status)
    }

    @Test
    fun `findById throws OrderNotFoundException for unknown id`() {
        val id = UUID.randomUUID()
        `when`(orderRepository.findById(id)).thenReturn(Optional.empty())

        assertThrows<OrderNotFoundException> { service.findById(id) }
    }

    @Test
    fun `findAll with no params returns all orders`() {
        val entities = listOf(anEntity(), anEntity())
        `when`(orderRepository.findAll()).thenReturn(entities)

        val result = service.findAll(null, null)

        assertEquals(2, result.size)
    }

    @Test
    fun `findAll with traderId filters by trader`() {
        val entities = listOf(anEntity("alice"))
        `when`(orderRepository.findAllByTraderId("alice")).thenReturn(entities)

        val result = service.findAll("alice", null)

        assertEquals(1, result.size)
        verify(orderRepository).findAllByTraderId("alice")
    }

    @Test
    fun `findAll with status filters by status`() {
        val entities = listOf(anEntity(status = OrderStatus.EXECUTED.name))
        `when`(orderRepository.findAllByStatus(OrderStatus.EXECUTED.name)).thenReturn(entities)

        val result = service.findAll(null, OrderStatus.EXECUTED.name)

        assertEquals(1, result.size)
    }

    @Test
    fun `findAll with both traderId and status uses combined query`() {
        val entities = listOf(anEntity("alice", OrderStatus.PENDING.name))
        `when`(orderRepository.findAllByTraderIdAndStatus("alice", OrderStatus.PENDING.name)).thenReturn(entities)

        val result = service.findAll("alice", OrderStatus.PENDING.name)

        assertEquals(1, result.size)
        verify(orderRepository).findAllByTraderIdAndStatus("alice", OrderStatus.PENDING.name)
    }
}
