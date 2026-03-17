package de.antrophos.demo.spring.kafka.trader.execution.kafka

import de.antrophos.demo.spring.kafka.trader.execution.ExecutionCompensationService
import de.antrophos.demo.spring.kafka.trader.shared.events.CompensationRequested
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class CompensationKafkaListener(private val compensationService: ExecutionCompensationService) {

    @KafkaListener(topics = ["compensation-requests"], groupId = "execution-service")
    fun onCompensationRequested(event: CompensationRequested) {
        compensationService.voidTrade(event.tradeId, event.orderId)
    }
}
