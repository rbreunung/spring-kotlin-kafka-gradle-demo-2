# Trade Execution Platform

A banking-domain Spring Boot / Kotlin / Kafka demo modeling securities order flow with saga orchestration, Resilience4j circuit-breakers, and exactly-once Kafka transactions.

**Tech Stack:** Spring Boot 3.x · Kotlin · Apache Kafka (KRaft) · Gradle (Kotlin DSL) · Resilience4j · Docker

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Required to run services locally. [Adoptium](https://adoptium.net) |
| Docker Desktop | Any recent | Required for Kafka (and full-stack mode). Must be running. |

No other installation needed — Gradle is included via the wrapper (`./gradlew`).

---

## Daily Dev Workflow

Fast iteration: Kafka runs in Docker, services run on the local JVM with IntelliJ/hot-reload support.

**Step 1 — Start Kafka:**
```bash
docker compose up -d
```
Kafka is available at `localhost:9092`. Wait for it to become healthy:
```bash
docker compose ps   # kafka should show "healthy"
```

**Step 2 — Start services** (each in a separate terminal):
```bash
./gradlew :order:bootRun           # REST API on http://localhost:8080
./gradlew :risk:bootRun
./gradlew :execution:bootRun
./gradlew :settlement:bootRun
./gradlew :notification:bootRun
./gradlew :saga-orchestrator:bootRun
```

**Step 3 — Stop Kafka when done:**
```bash
docker compose down
```

---

## Full Docker Workflow

Everything in containers — closest to production, good for demos and CI.

**Build and start everything:**
```bash
docker compose -f docker-compose.full.yml up --build
```
- Builds all JARs and Docker images from source
- Starts Kafka + all 6 services
- `OrderService` REST API available at `http://localhost:8080`
- Services wait for Kafka to be healthy before starting

**Stop everything:**
```bash
docker compose -f docker-compose.full.yml down
```

> **Note:** Code changes require `--build` to rebuild images. Use the daily dev workflow for fast iteration.

---

## Running Tests

```bash
# All modules (uses embedded Kafka — no Docker needed)
./gradlew test

# Single module
./gradlew :order:test
./gradlew :shared:test
```

Tests use Spring Kafka's embedded broker — no external Kafka required.

---

## Observability

The platform exposes metrics and distributed traces for all six services.

### Prometheus endpoints

| Service | URL |
|---|---|
| order-service | http://localhost:8080/actuator/prometheus |
| risk-service | http://localhost:8081/actuator/prometheus |
| execution-service | http://localhost:8082/actuator/prometheus |
| settlement-service | http://localhost:8083/actuator/prometheus |
| notification-service | http://localhost:8090/actuator/prometheus |
| saga-orchestrator | http://localhost:8085/actuator/prometheus |

### Zipkin distributed tracing

Start the full stack to enable tracing:

```bash
docker compose -f docker-compose.full.yml up --build
```

Zipkin UI: http://localhost:9411

### Custom business metrics

| Metric | Type | Description |
|---|---|---|
| `orders_placed_total` | Counter | Number of orders successfully placed |
| `saga_duration_seconds` | Timer | End-to-end saga duration, tagged by `outcome` |
| `settlement_attempts_total` | Counter | Settlement attempts, tagged by `outcome` (success/failure) |

---

## Project Structure

```
trader/
├── shared/              # Kotlin library: domain classes + Kafka event types
├── order/               # Spring Boot: REST POST /orders, DELETE /orders/{id} (port 8080)
├── risk/                # Spring Boot: Kafka consumer/producer (Resilience4j CB)
├── execution/           # Spring Boot: Kafka consumer/producer
├── settlement/          # Spring Boot: Kafka consumer/producer (Resilience4j retry)
├── notification/        # Spring Boot: Kafka consumer + WebSocket/STOMP push (port 8090)
├── saga-orchestrator/   # Spring Boot: saga state machine over Kafka
├── docker-compose.yml         # Kafka only (daily dev)
├── docker-compose.full.yml    # Kafka + all services (demo/CI)
└── docs/
    ├── project-idea.md        # Project purpose and learning goals
    └── arch/architecture.md   # System architecture and Kafka topics
```

## Saga Flow

**Happy path:**
```
OrderPlaced → RiskApproved → TradeExecuted → PositionSettled → TraderNotified
```

**User-initiated cancellation** (`DELETE /orders/{id}` while order is PENDING or RISK_APPROVED):
```
DELETE /orders/{id} → OrderCancelled → CANCELLATION_COMPLETE (saga) → CANCELLED (order)
```

**Post-execution cancellation** (order already executed — triggers compensation):
```
DELETE /orders/{id} → OrderCancelled → CANCEL_REQUESTED → CompensationRequested → TradeVoided → COMPENSATION_COMPLETE
```

Compensating events on settlement failure: `SettlementFailed` → `CompensationRequested` → `TradeVoided` → `COMPENSATION_COMPLETE`.

---

## Manual Testing

Start the full stack first (see [Full Docker Workflow](#full-docker-workflow) or [Daily Dev Workflow](#daily-dev-workflow)).

All examples use `ORDER_ID` captured from the place-order response. The saga-orchestrator is on port `8085`; the order service is on port `8080`.

---

### Place an order

**bash / zsh:**
```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"traderId":"trader-001","symbol":"AAPL","quantity":100,"side":"BUY"}' \
  | tee /tmp/order.json

ORDER_ID=$(cat /tmp/order.json | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Order ID: $ORDER_ID"
```

**PowerShell:**
```powershell
$response = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/orders" `
  -ContentType "application/json" `
  -Body '{"traderId":"trader-001","symbol":"AAPL","quantity":100,"side":"BUY"}'

$ORDER_ID = $response.id
Write-Host "Order ID: $ORDER_ID"
```

---

### Check order status

**bash / zsh:**
```bash
curl -s http://localhost:8080/orders/$ORDER_ID | python3 -m json.tool
```

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/orders/$ORDER_ID"
```

Expected `status` values in sequence: `PENDING` → `RISK_APPROVED` → `EXECUTED` → `SETTLED`

---

### Check saga state

**bash / zsh:**
```bash
curl -s http://localhost:8085/sagas/$ORDER_ID | python3 -m json.tool
```

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8085/sagas/$ORDER_ID"
```

Expected `step` values in sequence: `RISK_REQUESTED` → `RISK_APPROVED` → `EXECUTION_REQUESTED` → `EXECUTION_COMPLETE` → `SETTLEMENT_REQUESTED` → `SETTLED`

---

### Watch the happy path (poll until settled)

**bash / zsh:**
```bash
while true; do
  STATUS=$(curl -s http://localhost:8080/orders/$ORDER_ID | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
  STEP=$(curl -s http://localhost:8085/sagas/$ORDER_ID | python3 -c "import sys,json; print(json.load(sys.stdin)['step'])" 2>/dev/null || echo "n/a")
  echo "$(date +%H:%M:%S)  order=$STATUS  saga=$STEP"
  [[ "$STATUS" == "SETTLED" || "$STATUS" == "RISK_REJECTED" || "$STATUS" == "COMPENSATION_COMPLETE" ]] && break
  sleep 2
done
```

**PowerShell:**
```powershell
do {
  $order = Invoke-RestMethod -Uri "http://localhost:8080/orders/$ORDER_ID"
  $saga  = try { Invoke-RestMethod -Uri "http://localhost:8085/sagas/$ORDER_ID" } catch { @{step="n/a"} }
  Write-Host "$(Get-Date -Format HH:mm:ss)  order=$($order.status)  saga=$($saga.step)"
  Start-Sleep -Seconds 2
} until ($order.status -in @("SETTLED","RISK_REJECTED","COMPENSATION_COMPLETE"))
```

---

### Cancel an order (before execution)

Place a fresh order, then cancel it immediately before the saga advances past risk check.

**bash / zsh:**
```bash
ORDER_ID=$(curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"traderId":"trader-001","symbol":"MSFT","quantity":50,"side":"SELL"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

curl -s -X DELETE http://localhost:8080/orders/$ORDER_ID -w "\nHTTP %{http_code}\n"

# Poll until terminal
while true; do
  STATUS=$(curl -s http://localhost:8080/orders/$ORDER_ID | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
  echo "order=$STATUS"
  [[ "$STATUS" == "CANCELLED" || "$STATUS" == "COMPENSATION_COMPLETE" ]] && break
  sleep 1
done
```

**PowerShell:**
```powershell
$order = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/orders" `
  -ContentType "application/json" `
  -Body '{"traderId":"trader-001","symbol":"MSFT","quantity":50,"side":"SELL"}'
$ORDER_ID = $order.id

Invoke-RestMethod -Method Delete -Uri "http://localhost:8080/orders/$ORDER_ID"

do {
  $o = Invoke-RestMethod -Uri "http://localhost:8080/orders/$ORDER_ID"
  Write-Host "order=$($o.status)"
  Start-Sleep -Seconds 1
} until ($o.status -in @("CANCELLED","COMPENSATION_COMPLETE"))
```

Expected outcome: order status → `CANCELLED`, saga step → `CANCELLATION_COMPLETE`

---

### List orders

**bash / zsh:**
```bash
# All orders
curl -s http://localhost:8080/orders | python3 -m json.tool

# Filter by trader
curl -s "http://localhost:8080/orders?traderId=trader-001" | python3 -m json.tool

# Filter by status
curl -s "http://localhost:8080/orders?status=SETTLED" | python3 -m json.tool
```

**PowerShell:**
```powershell
# All orders
Invoke-RestMethod -Uri "http://localhost:8080/orders"

# Filter by trader
Invoke-RestMethod -Uri "http://localhost:8080/orders?traderId=trader-001"

# Filter by status
Invoke-RestMethod -Uri "http://localhost:8080/orders?status=SETTLED"
```

---

## Version Management

- **Plugin versions:** declared in root `build.gradle.kts` with `apply false`
- **Spring Boot + Kafka versions:** managed by Spring Boot BOM (via `org.springframework.boot` plugin)
- **Resilience4j version:** managed by Spring Cloud BOM (`dependencyManagement` in each service module)
- **BOM versions:** `gradle.properties` (`springBootVersion`, `springCloudVersion`)
