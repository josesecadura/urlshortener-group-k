# Feature: URL Management (Deactivation)

## Decision

**Owner module**: `links` | **Event-coupled**: N (owner-only action, no cross-module side effects by default)

Allows the creating user to deactivate or delete a shortened URL using a management key issued at creation. Deactivated URLs return 410 Gone.

**Traps**: management key stored as plaintext; key invalidation not propagated to cache replicas (stale 307 after deactivation); no atomic check-and-delete under concurrent requests.

**Technology hints**: `@Transactional`; secure random key; `@ControllerAdvice`; if coupled with caching, publish an invalidation event.

---

- **Weight:** 13

- **Event-coupled:** N

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [ ] `POST /api/managed-links` con `url=https://example.com` devuelve `201`, incluye `Location` con la URL corta y el JSON contiene `hash`, `url` y una `managementKey` no vacía; además incluye `Cache-Control: no-store`
- [ ] `POST /api/managed-links` con una URL no válida (`no-es-una-url`), vacía o formada solo por espacios devuelve `400` y no crea ninguna URL corta
- [ ] Al crear dos URLs administrables, cada una recibe una `managementKey` distinta y en la base de datos se guarda su hash SHA-256, nunca la clave original
- [ ] Antes de desactivar una URL administrable, `GET /{hash}` devuelve `307` con `Location` igual a la URL original
- [ ] `DELETE /api/managed-links/{hash}` con `Authorization: Bearer {managementKey}` correcta devuelve `204`; a partir de ese momento `GET /{hash}` devuelve `410` y no redirige al destino
- [ ] `DELETE /api/managed-links/{hash}` sin `Authorization`, con `Bearer` vacío o sin el formato `Bearer` devuelve `403` y la URL sigue activa
- [ ] `DELETE /api/managed-links/{hash}` con una clave incorrecta o con la clave de otra URL devuelve `403` y la URL no se desactiva
- [ ] `DELETE /api/managed-links/{hash}` con un `hash` inexistente y una cabecera `Authorization: Bearer ...` válida en formato devuelve `404`
- [ ] Al repetir la desactivación de una URL con su clave correcta, ambas peticiones devuelven `204` y solo se publica un `ShortUrlDeactivatedEvent`
- [ ] Una URL creada con `POST /api/link` sin clave de gestión no se puede desactivar mediante `DELETE /api/managed-links/{hash}`; devuelve `403` y sigue redirigiendo
- [ ] Tras una desactivación correcta, el módulo `analytics` recibe `ShortUrlDeactivatedEvent` y refleja el estado desactivado de la URL en sus datos; una petición rechazada con `403` no produce ese cambio
- [ ] Si una URL estaba almacenada en caché en las dos réplicas, después de desactivarla ninguna réplica debe seguir devolviendo `307`; ambas deben responder `410`

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
