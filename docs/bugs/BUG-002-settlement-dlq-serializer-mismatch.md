# BUG-002: Settlement service DLQ serializer mismatch causes infinite retry loop

Status: resolved
Severity: medium
Date: 2026-03-20
Reporter: rbreunung

## Environment

- OS: Windows 11 / Docker Desktop
- Runtime/Version: JVM 21
- Framework/App Version: Spring Boot 3.x, Spring Kafka 3.3.13, Kafka 3.9.1
- Relevant Config: Full Docker Compose stack (`docker-compose.full.yml`)

## Steps to Reproduce

1. Start full stack: `docker compose -f docker-compose.full.yml up -d`
2. Wait for all services to be ready
3. Place first order: `POST http://localhost:8080/orders` — completes to SETTLED normally
4. Place second order: `POST http://localhost:8080/orders`
5. Poll `GET http://localhost:8085/sagas/{orderId}` — saga stays at `SETTLEMENT_REQUESTED` indefinitely

## Expected Behavior

Both orders progress through the full saga and reach `SETTLED`, triggering a notification for each.

## Actual Behavior

The second order's saga stalls at `SETTLEMENT_REQUESTED`. The settlement service consumer retries offset 1 in an infinite loop.

```
Caused by: java.lang.ClassCastException: class de.antrophos.demo.spring.kafka.trader.shared.events.SettlementRequested cannot be cast to class [B
  at org.apache.kafka.common.serialization.ByteArraySerializer.serialize(ByteArraySerializer.java:19)
  ...
org.springframework.kafka.KafkaException: Dead-letter publication to dlq.settlements failed for: settlement-requests-0@1
  at org.springframework.kafka.listener.DeadLetterPublishingRecoverer.verifySendResult
  ...
[Consumer] Seeking to offset 1 for partition settlement-requests-0
Record in retry and not yet recovered
```

## Affected Component(s)

`settlement-service` — Kafka error handler / `DeadLetterPublishingRecoverer` configuration

## Severity

**medium** — first order per stack run completes correctly; workaround exists (full stack restart)

## Workaround

Restart the full Docker Compose stack. The first order placed after restart always completes successfully.

---

## Root Cause

`SettlementKafkaConfig` configured the DLQ producer with `ByteArraySerializer` for values. When `SettlementService.settle()` throws (after Resilience4j exhausts retries), the `DeadLetterPublishingRecoverer` receives the deserialized `SettlementRequested` object — not raw bytes. `ByteArraySerializer` cannot serialize a non-`byte[]` value, throwing `ClassCastException`. The DLQ publication fails, the error handler cannot recover the record, and the consumer seeks back to the failed offset and retries indefinitely.

## Fix Summary

Changed `VALUE_SERIALIZER_CLASS_CONFIG` in the DLQ producer from `ByteArraySerializer` to `JsonSerializer` and updated the producer factory type from `DefaultKafkaProducerFactory<String, ByteArray>` to `DefaultKafkaProducerFactory<String, Any>`. The `DeadLetterPublishingRecoverer` can now correctly serialize any deserialized event object to the DLQ.

- **Test added:** `settlement/src/test/kotlin/.../kafka/DeadLetterTopicServiceFailureTest.kt` — `valid SettlementRequested that fails processing is routed to dlq settlements`
- **Commit:** 77e9def
