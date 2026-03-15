package de.antrophos.demo.spring.kafka.trader.settlement.kafka

import de.antrophos.demo.spring.kafka.trader.settlement.SettlementService
import de.antrophos.demo.spring.kafka.trader.shared.events.SettlementRequested
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class SettlementKafkaListener(private val settlementService: SettlementService) {

    private val log = LoggerFactory.getLogger(SettlementKafkaListener::class.java)

    @KafkaListener(topics = ["settlement-requests"], groupId = "settlement-service")
    fun onSettlementRequested(event: SettlementRequested) {
        log.info("Received SettlementRequested for tradeId={}", event.trade.id)
        settlementService.settle(event.trade, event.order)
    }
}
