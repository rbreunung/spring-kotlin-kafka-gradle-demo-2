package de.antrophos.demo.spring.kafka.trader.risk.kafka

import de.antrophos.demo.spring.kafka.trader.shared.events.RiskApproved
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RiskEventPublisher(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun publishApproved(orderId: UUID) {
        kafkaTemplate.send("risk-results", orderId.toString(), RiskApproved(orderId))
    }

    fun publishRejected(orderId: UUID, reason: String) {
        kafkaTemplate.send("risk-results", orderId.toString(), RiskRejected(orderId, reason))
    }
}
