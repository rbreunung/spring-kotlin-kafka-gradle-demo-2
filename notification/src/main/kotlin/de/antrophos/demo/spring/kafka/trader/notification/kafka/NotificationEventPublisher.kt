package de.antrophos.demo.spring.kafka.trader.notification.kafka

import de.antrophos.demo.spring.kafka.trader.shared.events.TraderNotified
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class NotificationEventPublisher(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun publishTraderNotified(traderId: String, orderId: UUID, message: String) {
        kafkaTemplate.send("trader-notifications", orderId.toString(), TraderNotified(traderId, orderId, message))
    }
}
