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

### Criterios de aceptación

- [x] **Caché en Redis con TTL:** Al crear una URL acortada y realizar una redirección, se almacena en Redis la asociación `hash → URL original` con un tiempo de expiración (TTL). La redirección devuelve el código HTTP `307` y la cabecera `Location` correcta.

- [x] **Invalidación entre réplicas:** Cuando una URL almacenada en caché se desactiva o elimina, su entrada se invalida en todas las réplicas (`app1` y `app2`). Las solicitudes posteriores dejan de devolver `307` y responden con `410 Gone` si está desactivada o `404 Not Found` si ha sido eliminada.

- [x] **URL inexistente:** Si se solicita la redirección de un hash inexistente, la aplicación devuelve `404 Not Found` y no almacena ninguna entrada para ese hash en Redis.

- [x] **Desactivación no autorizada:** Si se intenta desactivar una URL sin autorización, la operación es rechazada y la entrada de caché permanece válida. La URL continúa redirigiendo correctamente con `307`.

- [x] **Recuperación ante fallos del broker:** Si el broker de mensajería deja de estar disponible durante una desactivación o eliminación, el evento de invalidación queda pendiente sin perderse. Cuando el broker vuelve a funcionar, el evento se procesa y la caché queda invalidada correctamente.

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
