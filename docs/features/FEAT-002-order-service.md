# FEAT-002: Order Service — REST API, Persistence, and Status Tracking

Status: in-progress
Date: 2026-03-12
Author: Claude

---

## Context & Motivation

FEAT-001 scaffolded the `:order` module with a stub `OrderService` that echoes back orders and no REST controller, no Kafka producer, and no persistence. This feature implements the order service as the fully functioning entry point to the trade execution saga: accepting orders via REST, persisting them with H2, publishing Kafka events, and tracking order status transitions as downstream saga events arrive.

The architecture document records `Persistence | In-memory (HashMap)` as the design decision; this feature supersedes that decision by introducing Spring Data JPA + H2 to support queryable, structured order state — a better foundation for status tracking across multiple async transitions.

## Goals

- [ ] Expose a REST API (`POST`, `GET`, `DELETE /orders`) with Bean Validation
- [ ] Persist orders in H2 via Spring Data JPA with a separate `OrderEntity` (`:shared` stays annotation-free)
- [ ] Track fine-grained order status through the full saga lifecycle
- [ ] Publish `OrderPlaced` and `OrderCancelled` events to the `orders` Kafka topic
- [ ] Consume downstream Kafka events (`RiskApproved`, `RiskRejected`, `TradeExecuted`, `PositionSettled`, `SettlementFailed`) to update order status
- [ ] Add `OrderCancelled` event to `:shared`
- [ ] Update `docs/arch/architecture.md` to reflect H2 persistence and new Kafka consumer role

## Non-Goals

- Cancellation of orders in states other than `PENDING` (deferred until saga orchestrator is implemented)
- Real-time WebSocket or SSE status push to clients
- Order amendment / partial fill handling
- Authentication or authorisation
- PostgreSQL or any external database
- Distributed transaction / exactly-once semantics

## Architecture

### CQRS-lite layered design

```
OrderController
  ├── OrderCommandService   — place order, cancel order (writes + Kafka produce)
  └── OrderQueryService     — find by id, list with optional filters (reads)
        └── OrderRepository (Spring Data JPA → H2)

OrderEventListener          — @KafkaListener; updates OrderEntity status via OrderCommandService
```

### Order Status Lifecycle

```
PENDING ──(RiskApproved event)────→ RISK_APPROVED
PENDING ──(RiskRejected event)────→ RISK_REJECTED      [terminal]
PENDING ──(DELETE /orders/{id})───→ CANCELLED           [terminal]

RISK_APPROVED ──(TradeExecuted event)──→ EXECUTED
EXECUTED ──(PositionSettled event)──────→ SETTLED        [terminal success]
EXECUTED ──(SettlementFailed event)─────→ EXECUTION_FAILED [terminal failure]
```

Invalid transitions (e.g. a duplicate event arriving after a terminal state) are logged at WARN level and skipped — no exception thrown, no state change.

### Kafka

**Producer** (key = `orderId.toString()`, topic = `orders`):

| Event | Trigger |
|---|---|
| `OrderPlaced` | `POST /orders` — order accepted and persisted |
| `OrderCancelled` | `DELETE /orders/{id}` — PENDING order cancelled |

**Consumer** (group-id `order-service`):

| Topic | Event type | Status transition |
|---|---|---|
| `risk-results` | `RiskApproved` | PENDING → RISK_APPROVED |
| `risk-results` | `RiskRejected` | PENDING → RISK_REJECTED |
| `executions` | `TradeExecuted` | Look up `OrderEntity` by `event.trade.orderId`; transition RISK_APPROVED → EXECUTED; persist `event.trade.id` as `tradeId` |
| `settlements` | `PositionSettled` | EXECUTED → SETTLED (entity looked up by `tradeId`) |
| `settlements` | `SettlementFailed` | EXECUTED → EXECUTION_FAILED (entity looked up by `tradeId`) |

Deserialization uses Spring Kafka's built-in type header mechanism: the `JsonSerializer` producer writes a `__TypeId__` header containing the fully-qualified class name on every message. The `JsonDeserializer` consumer reads that header to determine the target class, wrapped in `ErrorHandlingDeserializer` for poison-message safety.

Because `risk-results` carries both `RiskApproved` and `RiskRejected`, and `settlements` carries both `PositionSettled` and `SettlementFailed`, each `@KafkaListener` method for those topics must declare its parameter as `ConsumerRecord<String, Any>` and dispatch via Kotlin `when (record.value())` / `is` checks. Single-event topics (`executions`) may use a concrete type directly.

Poison messages are logged and skipped (no DLQ at this stage).

## Data Model

### New event in `:shared`

```kotlin
data class OrderCancelled(val orderId: UUID)
```

### `OrderEntity` (`:order` module only)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK; server-generated on POST |
| `traderId` | String | |
| `symbol` | String | Stock ticker, e.g. "AAPL" |
| `quantity` | Int | |
| `side` | String | Stored as the enum name (e.g. `request.side.name`). Never use `@Enumerated(EnumType.ORDINAL)` — store as VARCHAR string explicitly. |
| `status` | String | `OrderStatus` enum name. Stored as VARCHAR using `status.name` — never use `@Enumerated(EnumType.ORDINAL)`. |
| `tradeId` | UUID? | Nullable; populated on `TradeExecuted`; used to look up settlement events |
| `createdAt` | Instant | Set on creation |
| `updatedAt` | Instant | Updated on every status transition |

### `OrderStatus` enum (`:order` module)

`PENDING`, `RISK_APPROVED`, `RISK_REJECTED`, `EXECUTED`, `SETTLED`, `EXECUTION_FAILED`, `CANCELLED`

### `OrderRepository`

```kotlin
interface OrderRepository : JpaRepository<OrderEntity, UUID> {
    fun findAllByTraderId(traderId: String): List<OrderEntity>
    fun findAllByStatus(status: String): List<OrderEntity>
    fun findAllByTraderIdAndStatus(traderId: String, status: String): List<OrderEntity>
    fun findByTradeId(tradeId: UUID): OrderEntity?
}
```

`OrderQueryService.findAll(traderId, status)` selects the appropriate repository method: both provided → `findAllByTraderIdAndStatus`; only `traderId` → `findAllByTraderId`; only `status` → `findAllByStatus`; neither → `findAll()`.

## API Surface

### `POST /orders`

Accepts `PlaceOrderRequest`, validates, persists as `PENDING`, publishes `OrderPlaced`, returns `201 Created` with `OrderResponse`.

**Request body:**

```json
{
  "traderId": "trader-42",
  "symbol": "AAPL",
  "quantity": 100,
  "side": "BUY"
}
```

**Validation:** `traderId: String` `@NotBlank`; `symbol: String` `@NotBlank`; `quantity: Int` `@Min(1)`; `side: Side` — the `PlaceOrderRequest` DTO declares `side` as the `Side` enum type (from `:shared`), not `String`. Jackson rejects unrecognised string values before Bean Validation runs, throwing `HttpMessageNotReadableException`. A `@ControllerAdvice` must map this to the same `400` error response shape as Bean Validation failures (see Error Response Shape below).

**Response `201`:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "traderId": "trader-42",
  "symbol": "AAPL",
  "quantity": 100,
  "side": "BUY",
  "status": "PENDING",
  "tradeId": null,
  "createdAt": "2026-03-12T10:00:00Z",
  "updatedAt": "2026-03-12T10:00:00Z"
}
```

### `GET /orders/{id}`

Returns `200 OrderResponse` or `404` if not found.

### `GET /orders`

Optional query params: `traderId`, `status`. Returns `200 List<OrderResponse>`. Both params are optional and may be combined. An unrecognised `status` value silently returns an empty list (no `400`).

### `DELETE /orders/{id}`

- `404` if order not found
- `409 Conflict` if order status ≠ `PENDING`
- On success: sets status to `CANCELLED`, publishes `OrderCancelled`, returns `204 No Content`

### Error responses

| Scenario | HTTP status |
|---|---|
| Validation failure on POST | `400 Bad Request` with field errors |
| Order not found | `404 Not Found` |
| Cancel non-PENDING order | `409 Conflict` |

### Error Response Shape

All `400` responses (Bean Validation and Jackson deserialization failures) must return a consistent body. A `@ControllerAdvice` maps both `MethodArgumentNotValidException` and `HttpMessageNotReadableException` to this shape:

```json
{
  "errors": [
    { "field": "quantity", "message": "must be greater than or equal to 1" },
    { "field": "side", "message": "Invalid value. Accepted values: BUY, SELL" }
  ]
}
```

For `HttpMessageNotReadableException` on `side`, the error entry uses `"field": "side"` and a human-readable message listing accepted values. A `GlobalExceptionHandler : ResponseBodyAdvice` or `@ExceptionHandler` in a `@RestControllerAdvice` is the recommended implementation.

## Edge Cases & Error Handling

| Scenario | Expected Behavior |
|---|---|
| Kafka event arrives for unknown orderId | Log WARN, skip — no exception |
| Kafka event arrives for terminal-state order | Log WARN, skip — idempotency guard |
| `TradeExecuted` arrives but order in wrong state | Log WARN, skip |
| Settlement event with unknown `tradeId` | Log WARN, skip |
| Kafka deserialization fails (poison message) | `ErrorHandlingDeserializer` logs and skips |
| H2 unavailable at startup | Spring Boot fails fast — not expected in dev |
| POST with duplicate client-provided ID | IDs are server-generated; no deduplication needed |

## New Dependencies (`order/build.gradle.kts`)

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.springframework.boot:spring-boot-starter-validation")
runtimeOnly("com.h2database:h2")
```

## Configuration (`order/src/main/resources/application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:orderdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
  kafka:
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: order-service
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "de.antrophos.demo.spring.kafka.trader.shared.events,de.antrophos.demo.spring.kafka.trader.shared.domain"
        spring.json.use.type.headers: true
```

## Files to Create / Modify

| Path | Action |
|---|---|
| `shared/src/main/kotlin/.../shared/events/OrderCancelled.kt` | Create |
| `order/build.gradle.kts` | Modify — add JPA, H2, validation |
| `order/src/main/resources/application.yml` | Modify — H2, JPA, Kafka config |
| `order/src/main/kotlin/.../order/domain/OrderStatus.kt` | Create |
| `order/src/main/kotlin/.../order/domain/OrderEntity.kt` | Create |
| `order/src/main/kotlin/.../order/repository/OrderRepository.kt` | Create |
| `order/src/main/kotlin/.../order/dto/PlaceOrderRequest.kt` | Create |
| `order/src/main/kotlin/.../order/dto/OrderResponse.kt` | Create |
| `order/src/main/kotlin/.../order/service/OrderCommandService.kt` | Create |
| `order/src/main/kotlin/.../order/service/OrderQueryService.kt` | Create |
| `order/src/main/kotlin/.../order/kafka/OrderEventListener.kt` | Create |
| `order/src/main/kotlin/.../order/web/OrderController.kt` | Create |
| `order/src/main/kotlin/.../order/OrderService.kt` | Delete — replaced by command/query services |
| `docs/arch/architecture.md` | Modify — (1) add `OrderService` as consumer of `risk-results`, `executions`, `settlements` topics in the Kafka topics table; (2) correct `PositionService` → `SettlementService` in the `executions` row; (3) update the persistence technology decision row from `In-memory (HashMap)` to `H2 (Spring Data JPA)`; (4) add `OrderCancelled` to the events list |

## Acceptance Criteria

- [ ] `./gradlew :shared:build` — `OrderCancelled` compiles
- [ ] `./gradlew :order:test` — all tests pass (context loads, unit tests for command/query services, Kafka listener tests with embedded Kafka)
- [ ] `POST /orders` with valid body → `201` + PENDING status + `OrderPlaced` published to `orders` topic
- [ ] `POST /orders` with `quantity: 0` → `400` with field error
- [ ] `GET /orders/{id}` → `200` with correct order
- [ ] `GET /orders/{id}` for unknown id → `404`
- [ ] `GET /orders?traderId=trader-42` → filtered list
- [ ] `GET /orders?status=PENDING` → filtered list
- [ ] `DELETE /orders/{id}` on PENDING order → `204` + status `CANCELLED` + `OrderCancelled` published
- [ ] `DELETE /orders/{id}` on non-PENDING order → `409`
- [ ] Consuming `RiskApproved` event → order status transitions PENDING → RISK_APPROVED
- [ ] Consuming `RiskRejected` event → order status transitions PENDING → RISK_REJECTED
- [ ] Consuming `TradeExecuted` event → order looked up by `event.trade.orderId`; status transitions RISK_APPROVED → EXECUTED; `event.trade.id` persisted as `tradeId`
- [ ] Consuming `PositionSettled` event → order status transitions EXECUTED → SETTLED (lookup by tradeId)
- [ ] Consuming `SettlementFailed` event → order status transitions EXECUTED → EXECUTION_FAILED
- [ ] Invalid transition (e.g. duplicate event on terminal order) → logged and skipped; no exception

## Related Docs

- [Architecture](../arch/architecture.md)
- [FEAT-001: Create Modules](FEAT-001-create-modules.md)
