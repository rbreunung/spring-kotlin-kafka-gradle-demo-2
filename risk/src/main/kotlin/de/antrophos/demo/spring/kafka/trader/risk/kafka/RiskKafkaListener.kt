package de.antrophos.demo.spring.kafka.trader.risk.kafka

import de.antrophos.demo.spring.kafka.trader.risk.RiskService
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskCheckRequested
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RiskKafkaListener(private val riskService: RiskService) {

    @KafkaListener(topics = ["risk-checks"])
    fun onRiskCheckRequested(request: RiskCheckRequested) {
        riskService.handle(request)
    }
}
