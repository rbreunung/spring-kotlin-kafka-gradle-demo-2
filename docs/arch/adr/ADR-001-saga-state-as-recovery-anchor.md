# ADR-001: Saga State Entity as the Authoritative Recovery Anchor

Status: accepted
Date: 2026-03-13
Deciders: development team
Related Feature: FEAT-004

---

## Context

The `SagaOrchestrator` drives orders through a multi-step pipeline (risk → execution → settlement → notification). Each step transition is persisted in `SagaStateEntity` (H2 + Spring Data JPA). The state machine includes intermediate steps (`RISK_APPROVED`, `EXECUTION_COMPLETE`) as well as terminal steps (`RISK_REJECTED`, `SETTLEMENT_FAILED`, `SETTLED`).

FEAT-004 explicitly defers compensation and rollback logic to a future feature. When that feature is implemented, it will need to determine *where in the pipeline a saga was* at the time of failure in order to issue compensating actions (e.g., cancel a pending execution request, refund a settled position).

The question is: what is the authoritative source of truth for saga recovery?

## Decision

The `SagaStateEntity` — specifically its `step` field — is the authoritative recovery anchor for saga compensation. All intermediate states are persisted synchronously within the same transaction as the triggering Kafka event, before any downstream Kafka message is published. Compensation logic must read from `SagaStateEntity` to determine the rollback starting point.

## Consequences

**Positive:**
- Recovery state is immediately queryable via `GET /sagas/{orderId}` without replaying Kafka topics.
- Intermediate states (`RISK_APPROVED`, `EXECUTION_COMPLETE`) are visible to operators via the REST API, enabling manual inspection of in-flight sagas.
- Compensation logic has a clear, stable entry point: read `SagaStateEntity.step`, dispatch to the appropriate compensating action.

**Negative / Trade-offs:**
- `SagaStateEntity` and the Kafka publish are not in the same distributed transaction. If the JVM crashes after the DB commit but before the Kafka send, the saga state will be ahead of what downstream services have processed. Compensation must account for this (check whether downstream services received the command before compensating).
- H2 is in-memory; saga state is lost on restart. A production deployment would require a persistent store (PostgreSQL) before compensation is viable.

**Neutral:**
- All future features adding saga steps must persist each intermediate state before publishing downstream events. This is a convention that must be followed consistently — the STEP 4c implementation notes in `feature-impl.md` should reflect this.

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| Replay Kafka topics to reconstruct saga state | Requires retaining full topic history and replaying in order; complex and slow for compensation. Adds operational burden (topic retention policies). |
| Separate compensation event log (append-only table) | Adds a second write per transition; useful for full audit trails but overkill for the current scope. Can be added later without invalidating this decision. |
| In-memory saga state (no persistence) | Not viable — state is lost on restart and not observable via REST. Already rejected in FEAT-004 scope decision. |
