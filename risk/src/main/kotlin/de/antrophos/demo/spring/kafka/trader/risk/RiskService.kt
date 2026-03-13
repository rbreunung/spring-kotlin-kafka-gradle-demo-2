package de.antrophos.demo.spring.kafka.trader.risk

import de.antrophos.demo.spring.kafka.trader.risk.external.RiskEngineException
import de.antrophos.demo.spring.kafka.trader.risk.external.RiskExternalClient
import de.antrophos.demo.spring.kafka.trader.risk.kafka.RiskEventPublisher
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskCheckRequested
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.springframework.stereotype.Service

@Service
class RiskService(
    private val publisher: RiskEventPublisher,
    private val externalClient: RiskExternalClient
) {

    fun handle(request: RiskCheckRequested) {
        val order = request.order
        if (order.quantity > 10_000) {
            publisher.publishRejected(order.id, "quantity-exceeds-limit")
            return
        }
        try {
            if (externalClient.evaluate(order)) {
                publisher.publishApproved(order.id)
            }
        } catch (ex: RiskEngineException) {
            publisher.publishRejected(order.id, "evaluation-failed")
        } catch (ex: CallNotPermittedException) {
            publisher.publishRejected(order.id, "risk-service-unavailable")
        }
    }
}
