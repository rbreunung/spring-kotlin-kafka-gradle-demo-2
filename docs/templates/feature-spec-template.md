# FEAT-NNN: [Feature Title]

Status: draft
Date: YYYY-MM-DD
Author: [name]

## Progress

> Agent: update this section at each workflow step. Remove entire section when complete.

Current Step: [N]
Completed Steps: []
Last Updated: YYYY-MM-DD
Notes: [any state worth preserving across sessions]

---

## Context & Motivation

[Why is this feature needed? What problem does it solve? What triggered the request?]

## Goals

- [ ] [Goal 1 — specific and measurable]
- [ ] [Goal 2]

## Non-Goals

- [What this feature explicitly does NOT do — prevents scope creep]

## Architecture

[Describe the high-level design. How does this feature fit into the existing system?]

```mermaid
graph TD
    A[Existing Component] --> B[New Component]
    B --> C[External System]
```

### Key Flows

> Required: include a sequence diagram for every feature that involves inter-service communication,
> Kafka events, or multi-step data flow. This makes the expected data flow explicit and prevents
> implementation gaps (e.g., missing intermediate state saves, undocumented entity fields needed
> to republish earlier event data).

```mermaid
sequenceDiagram
    participant A as ServiceA
    participant B as ServiceB
    A-->>Kafka: EventName
    Kafka-->>B: EventName
    B->>B: persist state
    B-->>Kafka: ResponseEvent
```

## Data Model

[Describe new or changed data structures, entities, or schemas.]

```mermaid
erDiagram
    ENTITY {
        type field_name "description"
    }
    ENTITY ||--o{ OTHER : "relationship"
```

## API Surface / Interface

[Public interfaces, endpoints, methods, or events exposed or consumed by this feature.]

| Interface | Method/Type | Description |
|---|---|---|
| `endpoint or method` | GET / POST / fn | [what it does] |

## Edge Cases & Error Handling

| Scenario | Expected Behavior |
|---|---|
| [edge case] | [how the system responds] |

## Configuration

> Include this section when the feature introduces Spring Boot configuration (Kafka, JPA, etc.). Omit for features with no new configuration.

### `src/main/resources/application.yml`

```yaml
# new config keys introduced by this feature
```

### `src/test/resources/application.yml`

> Required for Kafka features: prevents KafkaAutoConfiguration from failing in @DataJpaTest
> and @WebMvcTest slices. Minimum required:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9999   # sentinel — no real broker needed
    listener:
      auto-startup: false
    # repeat producer/consumer serializer config from main application.yml
```

## Acceptance Criteria

- [ ] [Criterion 1 — testable, specific]
- [ ] [Criterion 2]

## Implementation Notes

> Agent: fill this section during feature-impl if implementation differs from spec.

[Any deviations from the design discovered during implementation.]

## Related Docs

- [FEAT-NNN: related feature](../features/FEAT-NNN-title.md)
- [ADR-NNN: architectural decision](../arch/adr/ADR-NNN-title.md)
