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

### Acceptance criteria

### Criterios de aceptación

- **Caché en Redis con TTL:** Al crear una URL acortada y realizar una redirección, se almacena en Redis la asociación `hash → URL original` con un tiempo de expiración (TTL). La redirección devuelve el código HTTP `307` y la cabecera `Location` correcta.

- **Invalidación entre réplicas:** Cuando una URL almacenada en caché se desactiva o elimina, su entrada se invalida en todas las réplicas (`app1` y `app2`). Las solicitudes posteriores dejan de devolver `307` y responden con `410 Gone` si está desactivada o `404 Not Found` si ha sido eliminada.

- **URL inexistente:** Si se solicita la redirección de un hash inexistente, la aplicación devuelve `404 Not Found` y no almacena ninguna entrada para ese hash en Redis.

- **Desactivación no autorizada:** Si se intenta desactivar una URL sin autorización, la operación es rechazada y la entrada de caché permanece válida. La URL continúa redirigiendo correctamente con `307`.

- **Recuperación ante fallos del broker:** Si el broker de mensajería deja de estar disponible durante una desactivación o eliminación, el evento de invalidación queda pendiente sin perderse. Cuando el broker vuelve a funcionar, el evento se procesa y la caché queda invalidada correctamente.


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
