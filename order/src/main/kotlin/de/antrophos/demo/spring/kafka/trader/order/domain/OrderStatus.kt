package de.antrophos.demo.spring.kafka.trader.order.domain

enum class OrderStatus {
    PENDING, RISK_APPROVED, RISK_REJECTED,
    EXECUTED,
    SETTLED, EXECUTION_FAILED,
    CANCELLED,
    COMPENSATION_IN_PROGRESS,  // non-terminal; set when SettlementFailed
    COMPENSATION_COMPLETE;     // terminal; set when TradeVoided

    val isTerminal: Boolean
        get() = when (this) {
            RISK_REJECTED, SETTLED, EXECUTION_FAILED, CANCELLED, COMPENSATION_COMPLETE -> true
            else -> false
        }
}
