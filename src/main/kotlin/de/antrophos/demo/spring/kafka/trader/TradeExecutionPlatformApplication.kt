package de.antrophos.demo.spring.kafka.trader

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TradeExecutionPlatformApplication

fun main(args: Array<String>) {
	runApplication<TradeExecutionPlatformApplication>(*args)
}
