package de.antrophos.demo.spring.kafka.trader.risk.kafka

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.core.KafkaTemplate
import java.util.UUID
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskApproved
import de.antrophos.demo.spring.kafka.trader.shared.events.RiskRejected

@ExtendWith(MockitoExtension::class)
class RiskEventPublisherTest {

    @Mock lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Test
    fun `publishApproved sends RiskApproved to risk-results with orderId as key`() {
        val publisher = RiskEventPublisher(kafkaTemplate)
        val orderId = UUID.randomUUID()

        publisher.publishApproved(orderId)

        verify(kafkaTemplate).send(
            org.mockito.Mockito.eq("risk-results"),
            org.mockito.Mockito.eq(orderId.toString()),
            org.mockito.Mockito.argThat { it is RiskApproved && it.orderId == orderId }
        )
    }

    @Test
    fun `publishRejected sends RiskRejected to risk-results with orderId as key and reason`() {
        val publisher = RiskEventPublisher(kafkaTemplate)
        val orderId = UUID.randomUUID()

        publisher.publishRejected(orderId, "quantity-exceeds-limit")

        verify(kafkaTemplate).send(
            org.mockito.Mockito.eq("risk-results"),
            org.mockito.Mockito.eq(orderId.toString()),
            org.mockito.Mockito.argThat { it is RiskRejected && it.orderId == orderId && it.reason == "quantity-exceeds-limit" }
        )
    }
}
