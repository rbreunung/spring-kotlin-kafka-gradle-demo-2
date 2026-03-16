package de.antrophos.demo.spring.kafka.trader.execution.repository

import de.antrophos.demo.spring.kafka.trader.execution.domain.TradeEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TradeRepository : JpaRepository<TradeEntity, UUID>
