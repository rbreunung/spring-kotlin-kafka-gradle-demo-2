package de.antrophos.demo.spring.kafka.trader.risk

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import org.springframework.stereotype.Service

@Service
class RiskService {

    fun evaluate(order: Order): Boolean = true
}
