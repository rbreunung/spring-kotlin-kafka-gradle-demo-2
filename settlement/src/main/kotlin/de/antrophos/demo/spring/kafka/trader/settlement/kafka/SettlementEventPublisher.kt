package de.antrophos.demo.spring.kafka.trader.settlement.kafka

import de.antrophos.demo.spring.kafka.trader.shared.domain.Position
import de.antrophos.demo.spring.kafka.trader.shared.events.PositionSettled
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementFailed
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SettlementEventPublisher(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    fun publishPositionSettled(tradeId: UUID, position: Position) {
        kafkaTemplate.send("settlements", tradeId.toString(), PositionSettled(tradeId, position))
    }

    fun publishSettlementFailed(tradeId: UUID, orderId: UUID, reason: String) {
        kafkaTemplate.send("settlements", tradeId.toString(), SettlementFailed(tradeId, orderId, reason))
    }
}
