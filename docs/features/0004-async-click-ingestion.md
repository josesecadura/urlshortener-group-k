# Feature: Async Click Ingestion via Outbox → Broker

## Decision

**Owner module**: `clicks` | **Event-coupled**: Y

Forwards `ClickLoggedEvent` to an external broker (`--profile broker`). The type lives in `clicks`. `links` already publishes it from `LinkService` on redirect. `@ApplicationModuleListener` and the JDBC Event Publication Registry (bootstrapped in the seed) already run the click write off the redirect thread. This feature externalises that in-process publication. Consumers are idempotent.

**Traps**: the seed publication is in-process — the broker path is a structural change (the Compose broker profile is a RabbitMQ stub, with AMQP still unwired); broker-down must leave the 307 in place; at-least-once delivery needs idempotency on the consumer (`eventId` already exists for the in-process listeners).

**Technology hints**: Spring Modulith `ApplicationEventPublicationRegistry`; RabbitMQ or Kafka with `--profile broker`; `@TransactionalEventListener(AFTER_COMMIT)`.

---

- **Weight:** 21

- **Event-coupled:** Y

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [ ] GET /$hash con hash siendo el correspondiente a una url valida responde 307 Temporary Redirect y
- [ ] GET /$hash publica en broker mensaje ClickLoggedIngestionEvent para que de forma asincrona se registre el evento e incremente el contador de clicks en el exchange clicks.ingest, con el hash y el eventId del click.
- [ ] El consumidor es idempotente, por cada click (lleva asociado ID), solo se registra en BD 1 vez, aunque el evento llegue mas veces.
- [ ] GET /$hash con hash siendo el correspondiente a una url valida, cuando el broker esta caido, responde 307 Temporary Redirect
- [ ] Con el broker caido, el click no se pierde, se almacena en Outbox transaccional, quedando como publicacion pendiente. El scheduler realiza periodicamente reintentos de entrega de estos mensajes
- [ ] GET /$hash con hash siendo el correspondiente a una url no existente, responde 404 Not Found y no publica ningun mensaje

### Scale evidence

Fill this section with the evidence of the scalability of the feature.

### Qualities

Fill **Assessed** with the grade you claim; **How to test** must falsify that number if it failed.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 0 \| 5 | Acceptance tests for this feature |
| **Scalability** | 0–3.5 | Scale evidence above. Not `ModularityTests`. |
| **Engineering** | 0 \| 0.75 \| 1.5 | Hard gates: AI disclosure + `./gradlew check`. Then ADR + `ModularityTests`: both → **1.5**, one → **0.75**. |

**Indicative total:** _ / 10

### AI disclosure

- **Tools / skills:** …
- **Used for:** …
- **Human-reviewed:** …
- Or: **No AI assistance** was used for this feature.
