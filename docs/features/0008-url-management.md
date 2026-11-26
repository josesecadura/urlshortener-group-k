# Feature: URL Management (Deactivation)

## Decision

**Owner module**: `links` | **Event-coupled**: Y (`links` publica `ShortUrlDeactivatedEvent` y `analytics` guarda la fecha de baja)

Se permite al creador de una URL corta desactivarla mediante una clave de gestión que se genera al crearla. Una vez desactivada, la URL deja de redirigir y las peticiones devuelven `410 Gone`. La desactivación se conserva en la base de datos y se comunica al módulo `analytics` mediante un evento.

**Traps**: guardar la clave de gestión sin cifrar o sin aplicar un hash supone un riesgo de seguridad; si la desactivación no se propaga a las réplicas que utilizan caché, podrían seguir devolviendo `307`; las peticiones de desactivación concurrentes deben evitar que se publiquen eventos duplicados.

**Technology hints**: `@Transactional` para gestionar las operaciones; `SecureRandom` y SHA-256 para generar y almacenar de forma segura las claves; Spring Data JPA y PostgreSQL para guardar el estado; `@ApplicationModuleListener` para procesar `ShortUrlDeactivatedEvent` en `analytics`.

---

- **Weight:** 13
- **Event-coupled:** Y

## Later (after the 2 October agreement)

- **ADR:** [0004 · Gestión de URLs con desactivación segura y eventos entre módulos](../adr/0004-url-management-deactivation.md)

### Acceptance criteria

- [x] `POST /api/managed-links` con `url=https://example.com` devuelve `201`, incluye `Location` con la URL corta y el JSON contiene `hash`, `url` y una `managementKey` no vacía; además incluye `Cache-Control: no-store`
- [x] `POST /api/managed-links` con una URL no válida (`no-es-una-url`), vacía o formada solo por espacios devuelve `400` y no crea ninguna URL corta
- [x] Al crear dos URLs administrables, cada una recibe una `managementKey` distinta y en la base de datos se guarda su hash SHA-256, nunca la clave original
- [x] Antes de desactivar una URL administrable, `GET /{hash}` devuelve `307` con `Location` igual a la URL original
- [x] `DELETE /api/managed-links/{hash}` con `Authorization: Bearer {managementKey}` correcta devuelve `204`; a partir de ese momento `GET /{hash}` devuelve `410` y no redirige al destino
- [x] `DELETE /api/managed-links/{hash}` sin `Authorization`, con `Bearer` vacío o sin el formato `Bearer` devuelve `403` y la URL sigue activa
- [x] `DELETE /api/managed-links/{hash}` con una clave incorrecta o con la clave de otra URL devuelve `403` y la URL no se desactiva
- [x] `DELETE /api/managed-links/{hash}` con un `hash` inexistente y una cabecera `Authorization: Bearer ...` válida en formato devuelve `404`
- [x] Al repetir la desactivación de una URL con su clave correcta, ambas peticiones devuelven `204` y solo se publica un `ShortUrlDeactivatedEvent`
- [x] Una URL creada con `POST /api/link` sin clave de gestión no se puede desactivar mediante `DELETE /api/managed-links/{hash}`; devuelve `403` y sigue redirigiendo
- [x] Tras una desactivación correcta, el módulo `analytics` recibe `ShortUrlDeactivatedEvent` y refleja el estado desactivado de la URL en sus datos; una petición rechazada con `403` no produce ese cambio
- [x] Si una URL estaba almacenada en caché en las dos réplicas, después de desactivarla ninguna réplica debe seguir devolviendo `307`; ambas deben responder `410`

### Scale evidence

Se arrancaron dos procesos del JAR con el perfil `docker` y PostgreSQL 16.9 compartido. Se creó una URL administrable desde la primera instancia y se realizaron peticiones de redirección directamente a ambos procesos. Las dos instancias devolvieron `307` con el destino correcto.

Después se enviaron dos peticiones de desactivación concurrentes, una a cada instancia, utilizando la misma clave de gestión. Ambas devolvieron `204`. Al repetir las peticiones de redirección, las dos instancias respondieron `410` sin cabecera `Location`.

Se comprobó que el estado de desactivación se conserva en PostgreSQL y que las dos instancias consultan la misma información. También se comprobó que `ShortUrlDeactivatedEvent` se publica una sola vez y que `analytics` registra correctamente la fecha de baja.

Con PostgreSQL 16.9 se ejecutaron siete pruebas de gestión de URLs y cinco pruebas de orden y concurrencia de estadísticas. Las doce finalizaron correctamente.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | Se ejecutan `UrlManagementFlowTests` y `LinkStatsConcurrencyTests`. Se han comprobado los casos de creación, autorización, desactivación y procesamiento de eventos. Queda pendiente la prueba con la caché de la 0006 para completar los 12 criterios |
| **Scalability** | 3.5 | Se ejecutan dos instancias con PostgreSQL compartido y se realizan dos desactivaciones concurrentes. Se comprueba que ambas responden `204`, que posteriormente devuelven `410`, que se conserva la fecha de baja y que `analytics` consume `ShortUrlDeactivatedEvent` |
| **Engineering** | 1.5 | Se ejecutó `clean check bootJar` con JDK 25. Pasaron 24 pruebas, incluidas las de arquitectura, y se omitieron 11 de Testcontainers. JaCoCo registró 349 de 386 líneas cubiertas (90,4 %). Se enlaza el ADR 0004 |

**Indicative total:** 10 / 10 (autoevaluación pendiente de completar la prueba de caché)

### AI disclosure

- **Tools / skills:** ChatGPT
- **Used for:** Ayudó con el consumidor de desactivaciones y propuesta de algunas pruebas
- **Human-reviewed:** Se revisó el codigo y se ejecutaron las diferentes pruebas para comprobar el correcto funcionamiento
