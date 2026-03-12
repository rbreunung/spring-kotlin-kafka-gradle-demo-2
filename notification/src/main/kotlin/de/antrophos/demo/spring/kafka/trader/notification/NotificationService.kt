package de.antrophos.demo.spring.kafka.trader.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationService {

    private val log = LoggerFactory.getLogger(NotificationService::class.java)

    fun notify(traderId: String, orderId: UUID, message: String) {
        log.info("Notification → trader={} order={} : {}", traderId, orderId, message)
    }
}
