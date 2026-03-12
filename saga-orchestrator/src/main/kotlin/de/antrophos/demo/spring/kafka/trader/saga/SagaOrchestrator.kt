package de.antrophos.demo.spring.kafka.trader.saga

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SagaOrchestrator {

    private val log = LoggerFactory.getLogger(SagaOrchestrator::class.java)

    fun start(order: Order) {
        log.info("Saga started for order={}", order.id)
    }
}
