package de.antrophos.demo.spring.kafka.trader.shared.events

import java.util.UUID

data class RiskApproved(val orderId: UUID)
