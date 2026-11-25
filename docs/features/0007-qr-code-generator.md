# Feature: QR Code Generation

## Decision

**Owner module**: `links` | **Event-coupled**: N

Generates a QR code PNG (or SVG) for any shortened URL on demand via content negotiation (`Accept: image/png`). Optionally embedded as a Base64 field in the creation response.

**Traps**: large QR payloads generated synchronously on every redirect poll; content negotiation conflicts with other features that extend the creation response; image format mismatch between on-the-fly generation and cached responses.

**Technology hints**: ZXing (core + javase); `@RequestMapping(produces = "image/png")`; Spring `StreamingResponseBody` for large images.

---

- **Weight:** 8

- **Event-coupled:** N

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [ ] **Generación del QR:** Al hacer `GET /{hash}` con la cabecera `Accept: image/png` para una URL existente y activa, el servidor devuelve `200 OK`, `Content-Type: image/png` y una imagen PNG válida con el código QR.

- [ ] **Contenido del QR:** Al leer el código QR generado, se obtiene la URL corta correspondiente al hash solicitado, no la URL original a la que redirige.

- [ ] **Redirección normal:** Al hacer `GET /{hash}` sin solicitar una imagen PNG, el servidor sigue devolviendo `307 Temporary Redirect`, con la URL original en la cabecera `Location`.

- [ ] **URL inexistente:** Si se solicita el QR de un hash que no existe con `Accept: image/png`, el servidor devuelve `404 Not Found` y no genera ningún QR.

- [ ] **URL desactivada:** Si se solicita el QR de una URL desactivada con `Accept: image/png`, el servidor devuelve `410 Gone` y no genera ningún QR.

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
