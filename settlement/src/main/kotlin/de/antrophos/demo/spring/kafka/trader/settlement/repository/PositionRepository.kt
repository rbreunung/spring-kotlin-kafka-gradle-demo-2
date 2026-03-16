package de.antrophos.demo.spring.kafka.trader.settlement.repository

import de.antrophos.demo.spring.kafka.trader.settlement.domain.PositionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PositionRepository : JpaRepository<PositionEntity, Long> {
    fun findByTraderIdAndSymbol(traderId: String, symbol: String): PositionEntity?
}
