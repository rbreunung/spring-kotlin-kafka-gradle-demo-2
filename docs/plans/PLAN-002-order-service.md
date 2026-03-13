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

## Related Docs

- [FEAT-002 Spec](../features/FEAT-002-order-service.md)
- [Architecture](../arch/architecture.md)
