# Risk Service — Module Notes for Agents

## Test Commands

```bash
./gradlew :risk:test                          # all tests
./gradlew :risk:test --tests "*.RiskServiceTest"                     # unit: service logic
./gradlew :risk:test --tests "*.RiskExternalClientTest"              # unit: CB wiring
./gradlew :risk:test --tests "*.RiskEventPublisherTest"              # unit: Kafka publisher
./gradlew :risk:test --tests "*.RiskKafkaIntegrationTest"            # integration: happy + poison
./gradlew :risk:test --tests "*.RiskCircuitBreakerIntegrationTest"   # integration: CB open/close
```

## Resilience4j: Use Programmatic CB, NOT `@CircuitBreaker` Annotation

`spring-cloud-starter-circuitbreaker-resilience4j` does **not** auto-configure
`FallbackExecutor`, which means `CircuitBreakerAspect` is never created and the
`@CircuitBreaker` annotation silently does nothing (exceptions propagate without
the CB recording failures or routing to fallback methods).

**Do this:**
```kotlin
private val cb = circuitBreakerRegistry.circuitBreaker("riskEngine")

fun evaluate(order: Order): Boolean = cb.executeSupplier {
    // ...
}
```

**Not this:**
```kotlin
@CircuitBreaker(name = "riskEngine", fallbackMethod = "evaluateFallback") // broken
fun evaluate(order: Order): Boolean { ... }
```

`CircuitBreakerRegistry` IS in the Spring context. Callers (`RiskService`) catch
`RiskEngineException` (failure counted) and `CallNotPermittedException` (CB open)
separately.

## No `ObjectMapper` Bean in Test Context

`spring-boot-starter` (no web) does not auto-configure a Jackson `ObjectMapper` bean.
Integration tests that need JSON deserialization must instantiate it directly:

```kotlin
private val objectMapper = ObjectMapper().registerKotlinModule()
```

## Circuit Breaker Configuration

Both `src/main/resources/application.yml` and `src/test/resources/application.yml`
must contain the `resilience4j.circuitbreaker.instances.riskEngine` block —
the test YAML replaces the main one (same filename), so the config is not inherited.
`minimumNumberOfCalls: 5` is set so `RiskCircuitBreakerIntegrationTest` can open the
CB after 5 calls without filling the full `slidingWindowSize: 10`.
