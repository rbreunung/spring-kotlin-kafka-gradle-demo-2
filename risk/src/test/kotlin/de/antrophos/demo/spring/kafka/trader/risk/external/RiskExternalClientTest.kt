package de.antrophos.demo.spring.kafka.trader.risk.external

import de.antrophos.demo.spring.kafka.trader.shared.domain.Order
import de.antrophos.demo.spring.kafka.trader.shared.domain.Side
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertTrue

class RiskExternalClientTest {

    private val order = Order(id = UUID.randomUUID(), traderId = "T1", symbol = "AAPL", quantity = 100, side = Side.BUY)

    private fun registryWithWindow(size: Int): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowType(COUNT_BASED)
                .slidingWindowSize(size)
                .minimumNumberOfCalls(size)
                .failureRateThreshold(100f)
                .build()
        )

    @Test
    fun `evaluate returns true when failure probability is 0`() {
        val client = RiskExternalClient(0.0, registryWithWindow(1))
        assertTrue(client.evaluate(order))
    }

    @Test
    fun `evaluate throws RiskEngineException when failure probability is 1`() {
        val client = RiskExternalClient(1.0, registryWithWindow(2))
        assertThrows<RiskEngineException> { client.evaluate(order) }
    }

    @Test
    fun `evaluate throws CallNotPermittedException once circuit breaker opens`() {
        // CB opens after 1 failure (window=1, minimumCalls=1, threshold=100%)
        val client = RiskExternalClient(1.0, registryWithWindow(1))

        assertThrows<RiskEngineException> { client.evaluate(order) }  // opens CB
        assertThrows<CallNotPermittedException> { client.evaluate(order) }  // CB is OPEN
    }
}
