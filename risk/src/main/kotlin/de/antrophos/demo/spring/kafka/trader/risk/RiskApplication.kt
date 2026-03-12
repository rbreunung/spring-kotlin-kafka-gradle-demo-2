package de.antrophos.demo.spring.kafka.trader.risk

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RiskApplication

fun main(args: Array<String>) {
    runApplication<RiskApplication>(*args)
}
