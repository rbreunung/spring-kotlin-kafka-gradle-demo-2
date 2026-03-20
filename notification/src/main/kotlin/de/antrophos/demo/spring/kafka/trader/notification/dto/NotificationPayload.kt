package de.antrophos.demo.spring.kafka.trader.notification.dto

import java.time.Instant
import java.util.UUID

data class NotificationPayload(
    val orderId: UUID,
    val message: String,
    val timestamp: Instant
)
