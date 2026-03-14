package de.antrophos.demo.spring.kafka.trader.execution.kafka

import de.antrophos.demo.spring.kafka.trader.shared.events.ExecutionRequested
import de.antrophos.demo.spring.kafka.trader.execution.ExecutionService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ExecutionKafkaListener(private val executionService: ExecutionService) {

    @KafkaListener(topics = ["execution-requests"])
    fun onExecutionRequested(event: ExecutionRequested) {
        executionService.execute(event.order)
    }
}
