package de.antrophos.demo.spring.kafka.trader.order.domain

enum class OrderStatus {
    PENDING, RISK_APPROVED, RISK_REJECTED, EXECUTED, SETTLED, EXECUTION_FAILED, CANCELLED;

    val isTerminal: Boolean
        get() = when (this) {
            RISK_REJECTED, SETTLED, EXECUTION_FAILED, CANCELLED -> true
            else -> false
        }
}
