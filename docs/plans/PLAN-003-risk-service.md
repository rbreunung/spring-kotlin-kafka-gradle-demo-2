# PLAN-003: Risk Service — Implementation Plan

Status: complete
Date: 2026-03-13
Feature: [FEAT-003](../features/FEAT-003-risk-service.md)

## Implementation Review

Status: complete
Reviewed: 2026-03-13

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `RiskCheckRequested` compiles | `EventsTest.RiskCheckRequested data class equality` | ✅ |
| `RiskApproved` published for quantity ≤ 10_000 | `RiskKafkaIntegrationTest.RiskCheckRequested with quantity within limit publishes RiskApproved` | ✅ |
| `RiskRejected("quantity-exceeds-limit")` for quantity > 10_000 | `RiskKafkaIntegrationTest.RiskCheckRequested with quantity exceeding limit publishes RiskRejected quantity-exceeds-limit` | ✅ |
| `RiskRejected("evaluation-failed")` when external call throws | `RiskServiceTest.publish evaluation-failed when external client throws RiskEngineException` | ✅ |
| CB opens after 50% failure rate; fallback `RiskRejected("risk-service-unavailable")` fires | `RiskCircuitBreakerIntegrationTest.CB opens after 5 failures and subsequent calls receive risk-service-unavailable` | ✅ |
| Poison message logged and skipped | `RiskKafkaIntegrationTest.poison message is logged and skipped with no result published` | ✅ |

Gaps: —

---

## Open Questions

- [x] Sliding window type → COUNT_BASED (10 calls, 50%, 10s wait)
- [x] CB fallback → `RiskRejected("risk-service-unavailable")`
- [x] External call failure mode → configurable probability (`risk.simulate-failure-probability`)
- [x] Idempotency → out of scope

---

## Vertical Slices

### Slice 1: Event Contract

**Status:** [x] done

### Slice 2: Kafka Consumer

**Status:** [x] done

### Slice 3: Kafka Producer

**Status:** [x] done

### Slice 4: Business Rule

**Status:** [x] done

### Slice 5: Circuit Breaker

**Status:** [x] done
