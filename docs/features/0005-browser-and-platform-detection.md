# Feature: Browser and Platform Detection

## Decision

**Owner module**: `analytics` (or `clicks`) | **Event-coupled**: Y (`ClickLoggedEvent` — enrich with User-Agent metadata)

Parses the `User-Agent` header on each redirect to identify browser family and operating system, storing the result for analytics. Aggregated stats available via a read-model endpoint.

**Traps**: raw `User-Agent` stored as-is is a privacy liability; parsing with regexes is fragile — use a dedicated library; rollup queries on millions of clicks without read-model materialisation will be slow.

**Technology hints**: `ua-parser-java`; listen to `ClickLoggedEvent` in the `analytics` module; `@Query` with JPA for aggregation; anonymise IP before storage.

---

- **Weight:** 8

- **Event-coupled:** Y

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [ ] Al hacer `GET /{hash}` de una URL activa con un `User-Agent` de Chrome en Windows, se devuelve `307` con `Location` igual a la URL original y se registra un clic con navegador `Chrome` y plataforma `Windows`
- [ ] Al hacer dos redirecciones con Chrome en Windows y otra con Firefox en Linux para el mismo `hash`, `GET /api/stats/{hash}/clients` devuelve `200` con `browsers.Chrome = 2`, `browsers.Firefox = 1`, `platforms.Windows = 2` y `platforms.Linux = 1` una vez procesados los eventos
- [ ] Al hacer `GET /{hash}` sin cabecera `User-Agent`, se mantiene la redirección `307` y el clic se contabiliza como `Unknown` tanto en navegador como en plataforma
- [ ] Al hacer `GET /{hash}` con un `User-Agent` no reconocible, se mantiene la redirección `307` y el clic se contabiliza como `Unknown` sin devolver un error de servidor
- [ ] `GET /api/stats/{hash}/clients` de una URL existente sin clics devuelve `200` con los objetos `browsers` y `platforms` vacíos
- [ ] `GET /api/stats/{hash}/clients` de un `hash` inexistente devuelve `404` y no crea estadísticas para ese identificador
- [ ] `GET /{hash}` de una URL inexistente devuelve `404` y no añade ningún clic a las estadísticas de navegadores o plataformas
- [ ] Si se procesa dos veces un mismo `ClickLoggedEvent` con el mismo `eventId`, las estadísticas por navegador y plataforma solo aumentan una vez
- [ ] El valor completo del `User-Agent` no se guarda en la base de datos; solo se almacenan los datos necesarios para las estadísticas agregadas

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
