package de.antrophos.demo.spring.kafka.trader.shared.events

import de.antrophos.demo.spring.kafka.trader.shared.domain.Position
import java.util.UUID

data class PositionSettled(val tradeId: UUID, val position: Position)
