package de.antrophos.demo.spring.kafka.trader.saga.domain

enum class SagaStep {
    RISK_REQUESTED,
    RISK_APPROVED, RISK_REJECTED,
    EXECUTION_REQUESTED, EXECUTION_COMPLETE,
    SETTLEMENT_REQUESTED,
    SETTLED, SETTLEMENT_FAILED,
    NOTIFICATION_SENT;

    val isTerminal: Boolean get() = this in setOf(RISK_REJECTED, SETTLEMENT_FAILED, SETTLED)
}
