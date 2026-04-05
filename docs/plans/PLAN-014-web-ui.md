# PLAN-014: Web UI — React Trader and Admin Dashboard — Implementation Plan

Status: draft
Date: 2026-04-05
Feature: [FEAT-014](../features/FEAT-014-web-ui.md)

## Progress

> Agent: update after each completed slice. Remove entire section when all slices done.

Current Slice: 0
Completed Slices: []
Last Updated: 2026-04-05

## Implementation Review

> Agent: fill this section during the final review step of feature-impl.

Status: pending
Reviewed: —

| Acceptance Criterion | Covering Test | Status |
|---|---|---|
| `:ui:bootRun` starts on 8091 serving React app | Spring Boot smoke test | pending |
| Trader can place an order via UI | `OrderProxyControllerTest` POST | pending |
| Order list updates on WebSocket notification | `useOrders` hook + manual | pending |
| Trader can cancel a PENDING order | `OrderProxyControllerTest` DELETE | pending |
| Admin page shows sagas, orders, health | `SagaProxyControllerTest`, `HealthAggregatorControllerTest` | pending |
| Health panel shows Resilience4j state | `HealthAggregatorControllerTest` | pending |
| `docker-compose.full.yml` includes `ui` service | Docker manual smoke test | pending |
| `:ui:test` runs proxy controller unit tests | All BFF test classes | pending |

Gaps: —

---

## Open Questions

- [ ] Exact method signature for `RestClient` to use when calling downstream services — look up `RestClient.get().uri(...).retrieve().body(String::class.java)` vs `toEntity()` during implementation
- [ ] Node version to pin in `build.gradle.kts` for the node-gradle plugin — check latest LTS at implementation time

---

## Vertical Slices

### Slice 1: Gradle module scaffold + Spring Boot app

**What it delivers:** `:ui` module exists, `./gradlew :ui:bootRun` starts on port 8091, `GET /` returns 200

**Files to touch:**
- `settings.gradle.kts` — add `include(":ui")`
- `ui/build.gradle.kts` — Spring Boot plugin, Kotlin, no node plugin yet
- `ui/src/main/kotlin/.../ui/UiApplication.kt` — `@SpringBootApplication` main
- `ui/src/main/resources/application.yml` — `server.port: 8091`, service URLs
- `ui/src/test/kotlin/.../ui/UiApplicationTest.kt` — `@SpringBootTest` context loads

**Test description:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` — context loads without errors

**Status:** [ ] todo

---

### Slice 2: BFF proxy — Order endpoints

**What it delivers:** `POST /api/orders`, `GET /api/orders`, and `DELETE /api/orders/{id}` proxy to OrderService; BFF controller tested with `MockRestServiceServer`

**Files to touch:**
- `ui/src/main/kotlin/.../ui/config/ServiceProperties.kt` — `@ConfigurationProperties("services")` with `orderUrl`, `sagaUrl`, etc.
- `ui/src/main/kotlin/.../ui/config/WebConfig.kt` — `RestClient` bean, SPA fallback route
- `ui/src/main/kotlin/.../ui/proxy/OrderProxyController.kt` — three endpoints
- `ui/src/test/kotlin/.../ui/proxy/OrderProxyControllerTest.kt` — `@WebMvcTest` + `MockRestServiceServer`

**Test description:** POST, GET, and DELETE each verified: correct upstream URL called, response status and body forwarded. DELETE 409 forwarded as-is.

**Status:** [ ] todo

---

### Slice 3: BFF proxy — Saga endpoint

**What it delivers:** `GET /api/sagas` proxies to SagaOrchestrator; controller tested

**Files to touch:**
- `ui/src/main/kotlin/.../ui/proxy/SagaProxyController.kt` — single GET endpoint
- `ui/src/test/kotlin/.../ui/proxy/SagaProxyControllerTest.kt` — `@WebMvcTest` + `MockRestServiceServer`

**Test description:** GET `/api/sagas` calls `{sagaUrl}/sagas` and returns the upstream JSON body.

**Status:** [ ] todo

---

### Slice 4: BFF health aggregator

**What it delivers:** `GET /api/health` calls `/actuator/health` on all 6 services concurrently, merges responses into `{services: {order: {status: "UP"}, ...}}`; controller tested

**Files to touch:**
- `ui/src/main/kotlin/.../ui/proxy/HealthAggregatorController.kt` — calls all 6 services, merges
- `ui/src/test/kotlin/.../ui/proxy/HealthAggregatorControllerTest.kt` — mock some UP, one DOWN; assert merged response

**Test description:** With 5 services returning `{status: "UP"}` and 1 returning `{status: "DOWN"}`, `GET /api/health` returns all 6 in a single JSON object with correct statuses.

**Status:** [ ] todo

---

### Slice 5: React app scaffold + Trader page (static)

**What it delivers:** Vite + React project under `ui/src/main/frontend/`; `npm run dev` starts on 5173 with `/api` proxy; `TraderPage` renders `OrderForm` and an empty `OrderList`; Vitest + React Testing Library wired up

**Files to touch:**
- `ui/src/main/frontend/` — scaffold via `npm create vite@latest` (React + TypeScript template)
- `ui/src/main/frontend/src/App.tsx` — React Router setup, nav header with `/` and `/admin` links
- `ui/src/main/frontend/src/pages/TraderPage.tsx` — renders `OrderForm` + `OrderList`
- `ui/src/main/frontend/src/components/trader/OrderForm.tsx` — fields: traderId, symbol, qty, side; client-side required validation
- `ui/src/main/frontend/src/components/trader/OrderList.tsx` — accepts `orders` prop, renders table or "no orders" placeholder
- `ui/src/main/frontend/vite.config.ts` — proxy `/api` → `http://localhost:8091`
- `ui/src/main/frontend/src/components/trader/OrderForm.test.tsx` — renders form, submit blocked when traderId empty

**Test description:** `OrderForm` renders four fields and a submit button; submitting with empty traderId does not call the API (client-side validation fires).

**Status:** [ ] todo

---

### Slice 6: React — place order and fetch order list

**What it delivers:** `OrderForm` submits `POST /api/orders`; `useOrders` hook fetches `GET /api/orders?traderId=...`; orders appear in `OrderList` after submit

**Files to touch:**
- `ui/src/main/frontend/src/api/client.ts` — axios instance, `baseURL: '/api'`
- `ui/src/main/frontend/src/hooks/useOrders.ts` — `useOrders(traderId)`: fetches orders, exposes `refresh()`
- `ui/src/main/frontend/src/pages/TraderPage.tsx` — wires `OrderForm` submit → POST → `refresh()`
- `ui/src/main/frontend/src/components/trader/OrderList.test.tsx` — renders rows from provided orders array

**Test description:** `OrderList` with two orders renders two rows with symbol, qty, status badge.

**Status:** [ ] todo

---

### Slice 7: React — real-time WebSocket notifications

**What it delivers:** `useTraderNotifications` opens STOMP WebSocket to `:8090/ws`, subscribes to `/topic/trader/{traderId}`; on each message, calls `refresh()` to re-fetch orders; order status updates without manual reload

**Files to touch:**
- `ui/src/main/frontend/src/hooks/useTraderNotifications.ts` — STOMP client setup, subscribe, reconnect logic
- `ui/src/main/frontend/src/pages/TraderPage.tsx` — use hook, pass `refresh` as callback
- `ui/src/main/frontend/package.json` — add `@stomp/stompjs` dependency

**Test description:** Manual test — place order, watch status badge update through RISK_APPROVED → EXECUTION_COMPLETE → SETTLED without page refresh.

**Status:** [ ] todo

---

### Slice 8: React — cancel order

**What it delivers:** Each `OrderRow` shows a Cancel button when status is `PENDING`; clicking calls `DELETE /api/orders/{id}`; order list refreshes; 409 from server shown as toast

**Files to touch:**
- `ui/src/main/frontend/src/components/trader/OrderRow.tsx` — Cancel button, visible only when status === 'PENDING'
- `ui/src/main/frontend/src/components/trader/OrderRow.test.tsx` — Cancel button visible for PENDING, hidden for SETTLED

**Test description:** `OrderRow` with status `PENDING` renders Cancel button; `OrderRow` with status `SETTLED` does not.

**Status:** [ ] todo

---

### Slice 9: React — Admin page

**What it delivers:** `/admin` page renders three stacked panels: `HealthPanel`, `SagaTable`, `AllOrdersTable`; each polls every 5s

**Files to touch:**
- `ui/src/main/frontend/src/pages/AdminPage.tsx` — stacked layout, three components
- `ui/src/main/frontend/src/components/admin/HealthPanel.tsx` — badge per service with UP/DOWN/DEGRADED color
- `ui/src/main/frontend/src/components/admin/SagaTable.tsx` — sagaId, orderId, state columns
- `ui/src/main/frontend/src/components/admin/AllOrdersTable.tsx` — traderId, symbol, qty, side, status columns
- `ui/src/main/frontend/src/components/admin/HealthPanel.test.tsx` — renders UP badge green, DOWN badge red

**Test description:** `HealthPanel` with `{order: {status: "UP"}, settlement: {status: "DOWN"}}` renders one green and one red badge.

**Status:** [ ] todo

---

### Slice 10: Gradle node-gradle build integration

**What it delivers:** `./gradlew :ui:bootJar` builds the React app via Vite and bundles it into the Spring Boot JAR; `./gradlew :ui:test` runs Kotlin tests and React tests

**Files to touch:**
- `ui/build.gradle.kts` — add `com.github.node-gradle.node` plugin; `npmInstall`, `npmBuild` tasks; Copy task → `src/main/resources/static/`; `bootJar.dependsOn(copyFrontend)`; `test.dependsOn(npmTest)`
- `build.gradle.kts` (root) — add node-gradle plugin version in `apply false` block

**Test description:** `./gradlew :ui:bootJar` completes; JAR contains `BOOT-INF/classes/static/index.html`; verified by listing JAR contents with `jar tf`.

**Status:** [ ] todo

---

### Slice 11: Docker — add `:ui` to `docker-compose.full.yml`

**What it delivers:** `docker compose -f docker-compose.full.yml up --build` (after `./gradlew bootJar`) starts all 7 services including the UI at `http://localhost:8091`

**Files to touch:**
- `ui/Dockerfile` — pre-built JAR pattern (ADR-003): `COPY ui/build/libs/*.jar app.jar`
- `docker-compose.full.yml` — add `ui` service with port 8091, env vars for service URLs, `depends_on: [order, saga-orchestrator, notification]`
- `docs/arch/architecture.md` — add `:ui` row to Services table and port 8091 to infrastructure table

**Test description:** `docker inspect <ui-container>` shows port 8091 exposed; `curl http://localhost:8091` returns HTML. Manual verification.

**Status:** [ ] todo

---

### Slice 12: Architecture doc + README updates

**What it delivers:** `docs/arch/architecture.md` reflects the new `:ui` service and port 8091; README manual testing section updated with UI instructions

**Files to touch:**
- `docs/arch/architecture.md` — add `:ui` to Services table, Gradle module layout, and infrastructure table
- `README.md` — add "Web UI" section to manual testing, note `http://localhost:8091` for trader and `/admin` for admin

**Test description:** Docs review — no references to six services where seven are now expected.

**Status:** [ ] todo
