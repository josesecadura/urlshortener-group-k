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

- **ADR:** [0007 · Generación asíncrona de códigos QR](../adr/0007-qr-code-generation.md)

### Acceptance criteria

- [x] **Generación del QR:** Al hacer `GET /{hash}` con la cabecera `Accept: image/png` para una URL existente y activa, el servidor devuelve `200 OK`, `Content-Type: image/png` y una imagen PNG válida con el código QR.

- [x] **Contenido del QR:** Al leer el código QR generado, se obtiene la URL corta correspondiente al hash solicitado, no la URL original a la que redirige.

- [x] **Redirección normal:** Al hacer `GET /{hash}` sin solicitar una imagen PNG, el servidor sigue devolviendo `307 Temporary Redirect`, con la URL original en la cabecera `Location`.

- [x] **URL inexistente:** Si se solicita el QR de un hash que no existe con `Accept: image/png`, el servidor devuelve `404 Not Found` y no genera ningún QR.

- [x] **URL desactivada:** Si se solicita el QR de una URL desactivada con `Accept: image/png`, el servidor devuelve `410 Gone` y no genera ningún QR.

### Scale evidence

La generación se ejecuta con `@Async` y devuelve un `CompletableFuture`, por lo que el hilo MVC
no codifica la imagen. `QrCodeFlowTests` comprueba que la solicitud PNG inicia procesamiento
asíncrono con `request().asyncStarted()`.

No se guarda ningún PNG ni estado de QR en memoria, Redis o PostgreSQL: cada réplica puede
generar la misma representación de forma independiente. Se arrancó el producto con
`./gradlew bootJar --rerun-tasks` y `docker compose up --build`. Las dos réplicas, Redis,
RabbitMQ, PostgreSQL y Nginx quedaron sanos. A través del balanceador se creó el hash `1001` y
`GET /1001` con `Accept: image/png` devolvió `200`, `Content-Type: image/png` y un PNG de
256 × 256 píxeles. Las peticiones sin `Accept`, con `Accept: */*` y con `Accept: text/html`
devolvieron `307`.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | `QrCodeFlowTests` cubre los cinco criterios y decodifica el PNG; `QrCodeServiceTests` verifica el adaptador ZXing. |
| **Scalability** | 3.5 | `@Async` desplaza la codificación y no existe estado de QR por réplica; la evidencia de Compose prueba el producto con dos réplicas. |
| **Engineering** | 1.5 | ADR 0007, `ModularityTests` y `./gradlew check` en verde; declaración de IA incluida. |

**Indicative total:** 10 / 10

### AI disclosure

- **Tools / skills:** OpenAI Codex y ChatGPT.
- **Used for:** Análisis de requisitos, propuesta e implementación de la generación QR, diseño de pruebas y revisión de código y documentación.
- **Human-reviewed:** Se revisaron los cambios, los criterios de aceptación, la selección del handler HTTP y los resultados de Gradle y Docker Compose.
