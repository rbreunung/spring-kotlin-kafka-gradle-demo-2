package de.antrophos.demo.spring.kafka.trader.notification

import de.antrophos.demo.spring.kafka.trader.notification.dto.NotificationPayload
import de.antrophos.demo.spring.kafka.trader.notification.kafka.NotificationEventPublisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.UUID

class NotificationServiceTest {

    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var eventPublisher: NotificationEventPublisher
    private lateinit var service: NotificationService

    @BeforeEach
    fun setUp() {
        messagingTemplate = mock()
        eventPublisher = mock()
        service = NotificationService(messagingTemplate, eventPublisher)
    }

    @Test
    fun `notify sends STOMP message and publishes TraderNotified`() {
        val traderId = "trader-1"
        val orderId = UUID.randomUUID()
        val message = "Order settled"

        service.notify(traderId, orderId, message)

        verify(messagingTemplate).convertAndSend(eq("/topic/trader/$traderId"), any<NotificationPayload>())
        verify(eventPublisher).publishTraderNotified(traderId, orderId, message)
    }

    @Test
    fun `notify still publishes TraderNotified when STOMP send throws`() {
        val traderId = "trader-1"
        val orderId = UUID.randomUUID()
        val message = "Order settled"

        doThrow(RuntimeException("STOMP error")).whenever(messagingTemplate)
            .convertAndSend(any<String>(), any<NotificationPayload>())

        service.notify(traderId, orderId, message)

        verify(eventPublisher).publishTraderNotified(traderId, orderId, message)
    }

    @Test
    fun `notify does not propagate exception when Kafka publish throws`() {
        val traderId = "trader-1"
        val orderId = UUID.randomUUID()
        val message = "Order settled"

        doThrow(RuntimeException("Kafka error")).whenever(eventPublisher)
            .publishTraderNotified(any(), any(), any())

        service.notify(traderId, orderId, message)

        verify(messagingTemplate).convertAndSend(eq("/topic/trader/$traderId"), any<NotificationPayload>())
    }
}
