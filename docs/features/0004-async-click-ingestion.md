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

- [x] GET /$hash con hash siendo el correspondiente a una url valida responde 307 Temporary Redirect y
- [x] GET /$hash publica en broker mensaje ClickLoggedIngestionEvent para que de forma asincrona se registre el evento e incremente el contador de clicks en el exchange clicks.ingest, con el hash y el eventId del click.
- [x] El consumidor es idempotente, por cada click (lleva asociado ID), solo se registra en BD 1 vez, aunque el evento llegue mas veces.
- [x] GET /$hash con hash siendo el correspondiente a una url valida, cuando el broker esta caido, responde 307 Temporary Redirect
- [x] Con el broker caido, el click no se pierde, se almacena en Outbox transaccional, quedando como publicacion pendiente. El scheduler realiza periodicamente reintentos de entrega de estos mensajes
- [x] GET /$hash con hash siendo el correspondiente a una url no existente, responde 404 Not Found y no publica ningun mensaje

### Scale evidence

Fill this section with the evidence of the scalability of the feature.

### Qualities

Fill **Assessed** with the grade you claim; **How to test** must falsify that number if it failed.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | Se ha cumplido con todos los criterios de aceptacion propuestos. Se publica mensaje en broker correctamente y se lee por el modulo de clicks que lleva el conteo. Si el broker no responde se queda pendiente y cuando vuelve a funcionar se envia |
| **Scalability** | 2 | Se ha probado 100 peticiones a la vez. Falta completar mas pruebas |
| **Engineering** | 1.5 | Se utiliza ./gradlew check para ejecutar los tests propuestos y todos se ejecutan correctamente. Modularity tests superados |

**Indicative total:** 8.5 / 10

### AI disclosure

- **Tools / skills:** Claude
- **Used for:** Se ha utilizado para entender el funcionamiento y como interatuar con rabbitmq, asi como apoyo para entender la documentacion consultada sobre rabbitmq y como integrarlo en spring
- **Human-reviewed:** Se ha revisado los cambios y solucion de problemas propuestos con lo especificado en la documentacion de spring consultada y se ha comprobado si era correcto. No se ha utilizado para generar codigo. Se ha comprobado el funcionamiento realizando distintos tests que probaban las funcionalidades

