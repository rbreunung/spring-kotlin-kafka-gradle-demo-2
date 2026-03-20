package de.antrophos.demo.spring.kafka.trader.notification.kafka

import de.antrophos.demo.spring.kafka.trader.notification.NotificationService
import de.antrophos.demo.spring.kafka.trader.shared.events.NotificationRequested
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NotificationKafkaListener(private val notificationService: NotificationService) {

    @KafkaListener(topics = ["notifications"], groupId = "notification-service")
    fun onNotificationRequested(event: NotificationRequested) {
        notificationService.notify(event.traderId, event.orderId, event.message)
    }
}
