package de.antrophos.demo.spring.kafka.trader.notification

import de.antrophos.demo.spring.kafka.trader.notification.dto.NotificationPayload
import de.antrophos.demo.spring.kafka.trader.notification.kafka.NotificationEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class NotificationService(
    private val messagingTemplate: SimpMessagingTemplate,
    private val eventPublisher: NotificationEventPublisher
) {

    private val log = LoggerFactory.getLogger(NotificationService::class.java)

    fun notify(traderId: String, orderId: UUID, message: String) {
        try {
            messagingTemplate.convertAndSend(
                "/topic/trader/$traderId",
                NotificationPayload(orderId, message, Instant.now())
            )
        } catch (e: Exception) {
            log.error("Failed to send STOMP notification to trader={} order={}: {}", traderId, orderId, e.message)
        }

        try {
            eventPublisher.publishTraderNotified(traderId, orderId, message)
        } catch (e: Exception) {
            log.error("Failed to publish TraderNotified for trader={} order={}: {}", traderId, orderId, e.message)
        }
    }
}
