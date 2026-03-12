package de.antrophos.demo.spring.kafka.trader.order.service

import de.antrophos.demo.spring.kafka.trader.order.domain.OrderEntity
import de.antrophos.demo.spring.kafka.trader.order.domain.OrderStatus
import de.antrophos.demo.spring.kafka.trader.order.dto.PlaceOrderRequest
import de.antrophos.demo.spring.kafka.trader.order.exception.OrderNotFoundException
import de.antrophos.demo.spring.kafka.trader.order.exception.OrderNotCancellableException
import de.antrophos.demo.spring.kafka.trader.order.repository.OrderRepository
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class OrderCommandServiceTest {

    @Mock lateinit var orderRepository: OrderRepository
    @Mock lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    private lateinit var service: OrderCommandService

    @BeforeEach
    fun setUp() {
        service = OrderCommandService(orderRepository, kafkaTemplate)
    }

    private fun pendingEntity(id: UUID = UUID.randomUUID()) = OrderEntity(
        id = id,
        traderId = "T1",
        symbol = "AAPL",
        quantity = 10,
        side = "BUY",
        status = OrderStatus.PENDING.name,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `place creates PENDING entity and publishes OrderPlaced`() {
        val request = PlaceOrderRequest(traderId = "T1", symbol = "AAPL", quantity = 10, side = Side.BUY)
        val captor = ArgumentCaptor.forClass(OrderEntity::class.java)
        `when`(orderRepository.save(captor.capture())).thenAnswer { captor.value }

        val response = service.place(request)

        assertEquals(OrderStatus.PENDING.name, response.status)
        assertEquals("T1", response.traderId)
        assertEquals("BUY", response.side)
        assertNull(response.tradeId)
        verify(kafkaTemplate).send(eq("orders"), anyString(), any())
    }

    @Test
    fun `cancel on PENDING order sets CANCELLED and publishes OrderCancelled`() {
        val id = UUID.randomUUID()
        val entity = pendingEntity(id)
        `when`(orderRepository.findById(id)).thenReturn(Optional.of(entity))
        `when`(orderRepository.save(any(OrderEntity::class.java))).thenAnswer { it.arguments[0] }

        service.cancel(id)

        assertEquals(OrderStatus.CANCELLED.name, entity.status)
        verify(kafkaTemplate).send(eq("orders"), eq(id.toString()), any())
    }

    @Test
    fun `cancel on non-PENDING order throws OrderNotCancellableException`() {
        val id = UUID.randomUUID()
        val entity = pendingEntity(id).apply { status = OrderStatus.EXECUTED.name }
        `when`(orderRepository.findById(id)).thenReturn(Optional.of(entity))

        assertThrows<OrderNotCancellableException> { service.cancel(id) }
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any())
    }

    @Test
    fun `cancel on unknown id throws OrderNotFoundException`() {
        val id = UUID.randomUUID()
        `when`(orderRepository.findById(id)).thenReturn(Optional.empty())

        assertThrows<OrderNotFoundException> { service.cancel(id) }
    }

    @Test
    fun `applyTransition updates status for valid transition`() {
        val id = UUID.randomUUID()
        val entity = pendingEntity(id)
        `when`(orderRepository.findById(id)).thenReturn(Optional.of(entity))
        `when`(orderRepository.save(any(OrderEntity::class.java))).thenAnswer { it.arguments[0] }

        service.applyTransition(id, OrderStatus.RISK_APPROVED, fromStatus = OrderStatus.PENDING)

        assertEquals(OrderStatus.RISK_APPROVED.name, entity.status)
    }

    @Test
    fun `applyTransition persists tradeId when provided`() {
        val id = UUID.randomUUID()
        val tradeId = UUID.randomUUID()
        val entity = pendingEntity(id).apply { status = OrderStatus.RISK_APPROVED.name }
        `when`(orderRepository.findById(id)).thenReturn(Optional.of(entity))
        `when`(orderRepository.save(any(OrderEntity::class.java))).thenAnswer { it.arguments[0] }

        service.applyTransition(id, OrderStatus.EXECUTED, tradeId, fromStatus = OrderStatus.RISK_APPROVED)

        assertEquals(OrderStatus.EXECUTED.name, entity.status)
        assertEquals(tradeId, entity.tradeId)
    }

    @Test
    fun `applyTransition on terminal order is silently skipped`() {
        val id = UUID.randomUUID()
        val entity = pendingEntity(id).apply { status = OrderStatus.SETTLED.name }
        `when`(orderRepository.findById(id)).thenReturn(Optional.of(entity))

        service.applyTransition(id, OrderStatus.EXECUTION_FAILED)

        assertEquals(OrderStatus.SETTLED.name, entity.status)
        verify(orderRepository, never()).save(any())
    }

    @Test
    fun `applyTransition on unknown orderId is silently skipped`() {
        val id = UUID.randomUUID()
        `when`(orderRepository.findById(id)).thenReturn(Optional.empty())

        service.applyTransition(id, OrderStatus.RISK_APPROVED) // no exception

        verify(orderRepository, never()).save(any())
    }

    @Test
    fun `applyTransition on wrong fromStatus is silently skipped`() {
        val id = UUID.randomUUID()
        val entity = pendingEntity(id) // status = PENDING
        `when`(orderRepository.findById(id)).thenReturn(Optional.of(entity))

        service.applyTransition(id, OrderStatus.EXECUTED, fromStatus = OrderStatus.RISK_APPROVED) // expects RISK_APPROVED but is PENDING

        assertEquals(OrderStatus.PENDING.name, entity.status)
        verify(orderRepository, never()).save(any())
    }
}
