# PLAN-002: Order Service — Implementation Plan

Status: complete
Date: 2026-03-12
Feature: [FEAT-002](../features/FEAT-002-order-service.md)

---

## Overview

9-task implementation plan for FEAT-002, executed via subagent-driven development. All tasks complete.

## Tasks

| # | Title | Status |
|---|---|---|
| 1 | Add `OrderCancelled` event to `:shared` | complete |
| 2 | Update build configuration (JPA, H2, Validation, Awaitility) | complete |
| 3 | Persistence layer (`OrderStatus`, `OrderEntity`, `OrderRepository`) | complete |
| 4 | DTOs (`PlaceOrderRequest`, `OrderResponse`) | complete |
| 5 | Exceptions and `GlobalExceptionHandler` | complete |
| 6 | `OrderCommandService` and `OrderQueryService` with unit tests | complete |
| 7 | `OrderController` with `@WebMvcTest` coverage | complete |
| 8 | `OrderEventListener` with `@EmbeddedKafka` integration tests | complete |
| 9 | Architecture doc update and final cleanup | complete |

## Design Decisions

- **Architecture:** CQRS-lite — `OrderCommandService` (writes) + `OrderQueryService` (reads)
- **Persistence:** H2 in-memory via Spring Data JPA; `OrderEntity` lives in `:order` only (`:shared` stays annotation-free)
- **Status storage:** Enum name stored as `VARCHAR` — no `@Enumerated`; `@Table(name = "orders")` avoids H2 reserved keyword
- **Kafka deserialization:** `JsonDeserializer` with type headers (`__TypeId__`) + `ErrorHandlingDeserializer`; multi-event topics use `ConsumerRecord<String, Any>` + `when`/`is` dispatch
- **State guard:** `applyTransition` has `fromStatus` parameter — rejects invalid transitions with WARN log (idempotency-safe)
- **Cancellation scope:** PENDING only (RISK_APPROVED deferred to saga orchestrator feature)

## Implementation Review

Status: passed
Reviewed: 2026-03-13

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `./gradlew :shared:build` — `OrderCancelled` compiles | `EventsTest.OrderCancelled data class equality` | passed |
| `./gradlew :order:test` — all tests pass | Full `:order:test` suite — 41 tests | passed |
| `POST /orders` valid → 201 + PENDING + `OrderPlaced` published | `OrderControllerTest.POST orders returns 201` + `OrderEventListenerTest` | passed |
| `POST /orders` quantity 0 → 400 | `OrderControllerTest.POST orders with quantity 0 returns 400` | passed |
| `GET /orders/{id}` → 200 | `OrderControllerTest.GET orders by id returns 200` | passed |
| `GET /orders/{id}` unknown → 404 | `OrderControllerTest.GET orders by id returns 404 for unknown id` | passed |
| `GET /orders?traderId=…` → filtered list | `OrderControllerTest.GET orders with traderId param filters list` | passed |
| `GET /orders?status=…` → filtered list | `OrderControllerTest.GET orders with status param filters list` | passed |
| `DELETE /orders/{id}` PENDING → 204 + CANCELLED + `OrderCancelled` published | `OrderControllerTest.DELETE orders by id returns 204` + `OrderCommandServiceTest` | passed |
| `DELETE /orders/{id}` non-PENDING → 409 | `OrderControllerTest.DELETE orders by id returns 409 for non-PENDING order` | passed |
| `RiskApproved` → PENDING → RISK_APPROVED | `OrderEventListenerTest.RiskApproved transitions PENDING to RISK_APPROVED` | passed |
| `RiskRejected` → PENDING → RISK_REJECTED | `OrderEventListenerTest.RiskRejected transitions PENDING to RISK_REJECTED` | passed |
| `TradeExecuted` → RISK_APPROVED → EXECUTED + tradeId saved | `OrderEventListenerTest.TradeExecuted transitions RISK_APPROVED to EXECUTED and saves tradeId` | passed |
| `PositionSettled` → EXECUTED → SETTLED | `OrderEventListenerTest.PositionSettled transitions EXECUTED to SETTLED via tradeId lookup` | passed |
| `SettlementFailed` → EXECUTED → EXECUTION_FAILED | `OrderEventListenerTest.SettlementFailed transitions EXECUTED to EXECUTION_FAILED via tradeId lookup` | passed |
| Duplicate event on terminal order → skip | `OrderEventListenerTest.duplicate event on terminal order is skipped` | passed |

Gaps: none

## Related Docs

- [FEAT-002 Spec](../features/FEAT-002-order-service.md)
- [Architecture](../arch/architecture.md)
