# FEAT-011: Cancel In-Flight Orders — User-Initiated Saga Rollback

Status: complete
Date: 2026-03-16
Author: Claude

---

## Context & Motivation

FEAT-008 implements system-triggered compensation when settlement fails. A related but
distinct scenario exists: a trader wants to cancel an order that is already being
processed — i.e., the saga is in-flight (past `PENDING`, before a terminal state).

This is fundamentally harder than settlement-failure compensation because:

1. The cancel request arrives from outside the saga (via REST) while the saga may be
   at any step: `RISK_REQUESTED`, `RISK_APPROVED`, `EXECUTION_REQUESTED`, etc.
2. Depending on which step the saga is in, a different service already holds
   in-flight state:
   - During risk check → `RiskService` is evaluating; no trade exists yet
   - During execution → `ExecutionService` is simulating; trade may or may not exist
   - During settlement → `SettlementService` request is already out; position may be
     partially updated
3. If two asynchronous events arrive at the saga concurrently — e.g., `RiskRejected`
   and an `OrderCancelled` — the saga must reach a consistent terminal state regardless
   of which arrives first.

This feature was split from FEAT-008 during the FEAT-008 spec session because the
scope significantly expands the compensation surface and requires additional design
work on race condition handling.

**Relationship to FEAT-008:** FEAT-008 establishes the compensation infrastructure
(`CompensationRequested`, `TradeVoided`, `TradeEntity`, `COMPENSATION_REQUESTED` /
`COMPENSATION_COMPLETE` saga steps). FEAT-011 reuses this infrastructure where a
trade exists, and adds new cancellation paths for stages where no trade exists yet.

---

## Open Design Questions

> These questions were raised during the FEAT-008 spec session and require resolution
> before this spec can be finalised. Use the feature-spec workflow to work through them.

### Q1: Cancellable window — which saga steps allow cancellation?

Options:
- **A) Cancel only before execution** — `RISK_REQUESTED`, `RISK_APPROVED`, `EXECUTION_REQUESTED`
  (no trade exists yet; clean abort). After execution: reject cancel with HTTP 409.
- **B) Cancel at any non-terminal step** — including `EXECUTION_COMPLETE`,
  `SETTLEMENT_REQUESTED` (compensation via `CompensationRequested` / `TradeVoided`).
- **C) Cancel only before risk check** — `PENDING` only (simplest; most restrictive).

Implication: Option B is the most complete but requires compensation at settlement-in-flight stage,
which is the hardest case (settlement service request already out).

### Q2: Race condition — concurrent cancel + saga event

The user raised the concern: if `RiskRejected` and `OrderCancelled` arrive simultaneously,
will the saga end in a consistent state?

Current analysis (from FEAT-008 session): The existing guards in `SagaOrchestrator` already
handle this:
- `isTerminalOrWarn()` — if one event transitions to a terminal state first, the second event
  is silently dropped
- `applyTransition()` terminal guard in `OrderCommandService` — same protection for order status

However, cancel introduces a new actor (REST API) that can set the saga step directly.
This needs careful design: the cancel REST endpoint must use the same atomic
"if not terminal, transition to CANCELLED" pattern as the existing event handlers.

### Q3: Cancel during settlement — is compensation needed?

If the saga is at `SETTLEMENT_REQUESTED` when the cancel arrives:
- The `SettlementService` request is already in-flight
- If settlement succeeds, we would need to reverse a completed position update
- If settlement fails (naturally), FEAT-008 compensation already fires

This may be out of scope for FEAT-011. Options:
- **A) Reject cancel at SETTLEMENT_REQUESTED and later** — simplest; tell the trader
  the order is too far along
- **B) Accept cancel but wait** — saga transitions to a `CANCEL_PENDING` step; when
  `PositionSettled` or `SettlementFailed` arrives, resolve accordingly

### Q4: `OrderCancelled` event vs REST-only cancel

Does the cancel REST endpoint:
- **A) Publish `OrderCancelled` to Kafka** — SagaOrchestrator consumes it (async)
- **B) Call the saga directly** — REST → SagaOrchestrator synchronous call (violates
  service boundary)
- **C) REST updates order to CANCELLED; separate polling/webhook handles saga abort**

Option A is most consistent with the event-driven architecture. The `OrderCancelled`
event already exists in `:shared` (published by `OrderService` but currently unused by
`SagaOrchestrator`).

---

## Preliminary Design Notes

### Likely new saga steps

```
CANCEL_REQUESTED      — saga received cancel; determining whether compensation needed
CANCELLATION_COMPLETE — terminal; clean abort (no trade was executed)
```

If a trade was already executed (EXECUTION_COMPLETE or later):
- Reuse `COMPENSATION_REQUESTED` → `COMPENSATION_COMPLETE` from FEAT-008

### Likely new order status

```
CANCELLATION_IN_PROGRESS   — non-terminal; cancel accepted, awaiting saga resolution
CANCELLED                  — terminal; already exists in OrderStatus enum
```

### Interaction with FEAT-008

When cancel arrives at EXECUTION_COMPLETE or SETTLEMENT_REQUESTED and a trade exists:
- The existing `CompensationRequested` / `TradeVoided` flow from FEAT-008 can be reused
- The trigger is user-initiated rather than system-triggered, but the compensation
  mechanism is the same

---

## Non-Goals (preliminary)

- No cancellation after `SETTLED` (position is final; refund is a separate business process)
- No partial fill cancellation (execution is all-or-nothing in this simulation)
- No cancel-cancel race conditions (one outstanding cancel per order)
- No timeout-based auto-cancel (deferred)

---

## Related Docs

- [FEAT-008: Saga Compensation — Rollback on Settlement Failure](FEAT-008-saga-compensation.md)
- [FEAT-004: Saga Orchestrator](FEAT-004-saga-orchestrator.md)
- [ADR-001: Saga state as recovery anchor](../arch/adr/ADR-001-saga-state-as-recovery-anchor.md)
- [Architecture](../arch/architecture.md)
