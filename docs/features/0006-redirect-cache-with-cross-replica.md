# Feature: Redirect Cache with Cross-Replica Invalidation

## Decision

**Owner module**: `links` | **Event-coupled**: Y

Caches hash → target in a shared store. When a URL is deactivated or deleted, a `ShortUrlUpdatedEvent` triggers cache invalidation across all replicas.

**Traps**: an in-process `@Cacheable` is not shared across replicas — clients get stale targets from the non-invalidating replica; TTL-only eviction doesn’t satisfy the Level 4 bar.

**Technology hints**: Redis EXPIRE + pub/sub invalidation; Spring Cache with `RedisCacheManager`; event-driven invalidation subscriber.

---

- **Weight:** 21

- **Event-coupled:** Y

## Later (after the 2 October agreement)

- **ADR:** [0006-redirect-cache-invalidation](../adr/0006-redirect-cache-invalidation.md)

### Criterios de aceptación corregidos por el profesor

#### Success

- [X] `GET /{hash}` for an unknown hash returns **404** `ProblemDetails`, and that 404 is not cached
- [X] If that hash is created afterwards, the first `GET /{hash}` returns **307** with `Location` = target
- [X] `GET /{hash}` for an active hash returns **307** with `Location` = target
- [X] A later `GET /{hash}` of such active hash returns **307** with the `Location` cached

#### Rejection

- [X] After the action stops the redirect, `GET /{hash}` return **410** Problem Detail without waiting for the cached entry's TTL to expire.
- [X] While the link is stopped, the response for `GET /{hash}` is **410** Problem Detail
- [X] After the link is deleted, the response for `GET /{hash}` is **404** Problem Detail

#### Both replicas

- [X] The active **307**, and the stopped **410**, hold the same response when the load balancer sends the `GET /{hash}` to any replica. A broker outage does not delay stopping a redirect (no stale **307**).


### Scale evidence

- **Redis compartido.** Las 2 réplicas comparten la misma caché Redis. En Compose, `app1` llenó la caché y la desactivación en `app2` dejó ambos GET en `410`. En la prueba de borrado físico, el barrido eliminó la clave antes de que venciera su TTL y las dos réplicas devolvieron `404`. [RedisRedirectCacheTests.kt](../../src/test/kotlin/es/unizar/urlshortener/links/RedisRedirectCacheTests.kt)
- **Concurrencia.** El test controla el orden de las peticiones con PostgreSQL y Redis reales. La desactivación espera mientras una redirección rellena Redis. Al terminar, la clave queda borrada y el GET devuelve `410`. [RedirectCacheConcurrencyPostgresTests.kt](../../src/test/kotlin/es/unizar/urlshortener/links/RedirectCacheConcurrencyPostgresTests.kt)
- **Nivel 4.** `ShortUrlUpdatedEvent` sale de `links`, se consume en `cacheinvalidation` y viaja por RabbitMQ hacia una cola compartida. Los tests comprueban el envío tras el commit, el rollback, los mensajes sin ruta, el borrado en Redis y el reenvío automático tras la caída del broker. [RedirectCacheRabbitmqTests.kt](../../src/test/kotlin/es/unizar/urlshortener/links/RedirectCacheRabbitmqTests.kt) · [RedirectCacheRecoveryTests.kt](../../src/test/kotlin/es/unizar/urlshortener/links/RedirectCacheRecoveryTests.kt)
- **Carga del producto.** `docker compose --profile load run --rm k6` ejecutó 1.404 comprobaciones de creación y redirección con 5 usuarios virtuales durante 30 segundos. Hubo 0 fallos, 46,48 peticiones/s y un p95 de 13,46 ms. [load.k6.js](../../scripts/load.k6.js)

### Qualities

`./gradlew check --rerun-tasks` pasó en local con 55 tests, 0 fallos y 0 omitidos. La CI de esta versión está pendiente. Las puntuaciones de Correctness y Engineering son propuestas para cuando se valide la entrega.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5/5 propuestos | Ejecutar los tests vinculados a los cinco criterios y verificar su resultado en la CI de entrega. |
| **Scalability** | 3,5/3,5 | Reproducir las evidencias de nivel 4 con dos réplicas, Redis compartido, RabbitMQ y recuperación automática. Ejecutar k6 y comprobar que no falla ninguna creación ni redirección. |
| **Engineering** | 1,5/1,5 propuestos | Revisar la declaración de IA y el ADR. Ejecutar `./gradlew check --rerun-tasks` y `ModularityTests`, y comprobar la CI de entrega. |

**Indicative total** 10/10 condicionado a la validación de la CI de entrega.

### AI disclosure

- **Herramientas.** Codex y ChatGPT.
- **Uso.** Análisis del código y los requisitos, implementación, diseño y ejecución de pruebas, y revisión de la documentación.
- **Revisión humana.** He revisado los cambios propuestos, comprobado que cumplieran los requisitos de la feature y discutido las soluciones cuando encontraba algún problema.
