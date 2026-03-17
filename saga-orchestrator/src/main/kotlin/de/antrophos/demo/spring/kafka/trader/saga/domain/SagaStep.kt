package de.antrophos.demo.spring.kafka.trader.saga.domain

enum class SagaStep {
    RISK_REQUESTED,
    RISK_APPROVED, RISK_REJECTED,
    EXECUTION_REQUESTED, EXECUTION_COMPLETE,
    SETTLEMENT_REQUESTED,
    SETTLED,
    SETTLEMENT_FAILED,           // audit step — transitions immediately to COMPENSATION_REQUESTED
    COMPENSATION_REQUESTED,      // new
    COMPENSATION_COMPLETE,       // new (terminal)
    COMPENSATION_FAILED,         // new (terminal — reserved)
    NOTIFICATION_SENT;           // reserved — not yet activated

    val isTerminal: Boolean get() = this in setOf(
        RISK_REJECTED, SETTLED, COMPENSATION_COMPLETE, COMPENSATION_FAILED
    )
}
