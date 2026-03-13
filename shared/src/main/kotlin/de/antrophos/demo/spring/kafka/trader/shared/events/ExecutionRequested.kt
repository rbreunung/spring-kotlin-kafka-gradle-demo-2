package de.antrophos.demo.spring.kafka.trader.shared.events

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order

data class ExecutionRequested(val order: Order)
