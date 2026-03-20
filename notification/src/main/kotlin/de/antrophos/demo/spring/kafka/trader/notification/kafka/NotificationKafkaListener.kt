package de.antrophos.demo.spring.kafka.trader.notification.kafka

import de.antrophos.demo.spring.kafka.trader.notification.NotificationService
import de.antrophos.demo.spring.kafka.trader.shared.events.NotificationRequested
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NotificationKafkaListener(private val notificationService: NotificationService) {

    private val log = LoggerFactory.getLogger(NotificationKafkaListener::class.java)

    @KafkaListener(topics = ["notifications"])
    fun onNotificationRequested(event: NotificationRequested) {
        log.info("Received NotificationRequested for traderId={} orderId={}", event.traderId, event.orderId)
        notificationService.notify(event.traderId, event.orderId, event.message)
    }
}
