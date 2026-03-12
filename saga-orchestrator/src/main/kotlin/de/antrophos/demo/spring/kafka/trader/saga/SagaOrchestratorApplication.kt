package de.antrophos.demo.spring.kafka.trader.saga

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SagaOrchestratorApplication

fun main(args: Array<String>) {
    runApplication<SagaOrchestratorApplication>(*args)
}
