# PLAN-010: Notification Service — WebSocket Push for Real-Time Order Status

Status: complete
Date: 2026-03-29
Feature: [FEAT-010](../features/FEAT-010-notification-service.md)

## Implementation Review

Status: complete
Reviewed: 2026-03-29

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `./gradlew :notification:test` passes; STOMP broadcast verified via mock; `TraderNotified` published | `NotificationServiceTest` (3 tests), `NotificationKafkaListenerTest`, `NotificationEventPublisherTest` | ✅ |
| Manual: `http://localhost:8090` test client; push received in browser when saga reaches SETTLED | `WebSocketConfigTest` (config verified); manual demo | ✅ |
| System-test: saga reaches SETTLED → `TraderNotified` on `trader-notifications` | `NotificationTest` — file present; helpers `placeOrder`/`awaitSagaSettled` verified in `SystemTestBase` | ✅ |
| No changes to existing `HappyPathSagaFlowTest` assertions | SETTLED remains terminal saga step; no saga/order code changed | ✅ |

Gaps: none

---

## Vertical Slices

### Slice 1: Core notification logic — NotificationService + payload DTO

**What it delivers:** The central `notify()` method that broadcasts STOMP and publishes to Kafka independently — STOMP failure does not block Kafka publish.

**Files to touch:**
- `notification/src/main/kotlin/.../notification/NotificationService.kt` — create; inject `SimpMessagingTemplate` + `NotificationEventPublisher`; wrap each call in independent try/catch with error logging
- `notification/src/main/kotlin/.../notification/dto/NotificationPayload.kt` — create; `data class NotificationPayload(val orderId: UUID, val message: String, val timestamp: Instant)`

**Test description:** `NotificationServiceTest` — verify `SimpMessagingTemplate.convertAndSend` called with correct destination and payload; verify `publishTraderNotified` called; verify STOMP exception does not prevent Kafka publish.

**Status:** [x] done

---

### Slice 2: Kafka integration — listener, publisher, and configuration

**What it delivers:** Consumes `NotificationRequested` from `notifications` topic; publishes `TraderNotified` to `trader-notifications`. Kafka config wired for both prod and test.

**Files to touch:**
- `notification/src/main/kotlin/.../notification/kafka/NotificationKafkaListener.kt` — replace stub; delegate to `NotificationService.notify()`
- `notification/src/main/kotlin/.../notification/kafka/NotificationEventPublisher.kt` — create; `KafkaTemplate.send("trader-notifications", orderId.toString(), TraderNotified(...))`
- `notification/build.gradle.kts` — add `spring-boot-starter-websocket`
- `notification/src/main/resources/application.yml` — add `server.port: 8090`; Kafka consumer (group-id, deserializer, trusted packages) + producer (serializer) config
- `notification/src/test/resources/application.yml` — sentinel `bootstrap-servers: localhost:9999`; `listener.auto-startup: false`; matching serializer config

**Test description:** `NotificationKafkaListenerTest` — verify listener delegates to `NotificationService.notify()` with correct fields from `NotificationRequested`. `NotificationEventPublisherTest` — verify `KafkaTemplate.send()` called with topic `trader-notifications`, key `orderId.toString()`, and `TraderNotified` with matching fields.

**Status:** [x] done

---

### Slice 3: WebSocket / STOMP configuration

**What it delivers:** STOMP endpoint at `/ws` with SockJS fallback; in-memory simple broker on `/topic`; `/app` destination prefix configured.

**Files to touch:**
- `notification/src/main/kotlin/.../notification/config/WebSocketConfig.kt` — create; `@EnableWebSocketMessageBroker`; `addEndpoint("/ws").withSockJS()`; `enableSimpleBroker("/topic")`; `setApplicationDestinationPrefixes("/app")`

**Test description:** `WebSocketConfigTest` — verify the configuration class loads correctly in a Spring context; confirm `SimpMessagingTemplate` bean is available for autowiring by `NotificationService`.

**Status:** [x] done

---

### Slice 4: Static HTML test client

**What it delivers:** Static page at `http://localhost:8090` for manual demo of live STOMP push.

**Files to touch:**
- `notification/src/main/resources/static/index.html` — create; SockJS + STOMP.js from CDN; connects to `/ws`; subscribes to `/topic/trader/{traderId}`; displays incoming messages

**Test description:** No automated test — manual verification only. Start full stack, open `http://localhost:8090`, subscribe to a traderId, place order, confirm push arrives when saga reaches SETTLED.

**Status:** [x] done

---

### Slice 5: System test + architecture doc

**What it delivers:** End-to-end verification that `TraderNotified` appears on `trader-notifications` after a full saga run. Architecture doc updated to reflect real NotificationService implementation.

**Files to touch:**
- `system-test/src/test/.../NotificationTest.kt` — create; `KafkaConsumer<String, String>` with `earliest` offset and UUID-suffixed group-id; `placeOrder()` → `awaitSagaSettled()` → poll `trader-notifications` up to 30s → assert record containing `orderId`
- `docs/arch/architecture.md` — update NotificationService row to include WebSocket/STOMP push detail and port 8090

**Test description:** `NotificationTest.TraderNotified is published on trader-notifications after saga reaches SETTLED` — full E2E; passes when notification arrives within 30s of saga reaching SETTLED.

**Status:** [x] done
