package de.antrophos.demo.spring.kafka.trader.risk.external

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

@Component
class RiskExternalClient(
    @Value("\${risk.simulate-failure-probability}") private val failureProbability: Double,
    circuitBreakerRegistry: CircuitBreakerRegistry
) {
    private val cb = circuitBreakerRegistry.circuitBreaker("riskEngine")

    fun evaluate(order: Order): Boolean {
        return cb.executeSupplier {
            if (ThreadLocalRandom.current().nextDouble() < failureProbability) {
                throw RiskEngineException("Simulated risk engine failure")
            }
            true
        }
    }
}
