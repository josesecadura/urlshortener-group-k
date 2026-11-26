# ADR 0007: Generación asíncrona de códigos QR

- **Estado:** Aceptado
- **Fecha:** 2026-11-26
- **Responsables:** Grupo k / ficha [`0007`](../features/0007-qr-code-generator.md)

## Contexto

La ficha 0007 añade una representación PNG de una URL corta cuando el cliente solicita
`Accept: image/png`. La feature pertenece a `links`, no necesita comunicarse con otros módulos
y tiene peso 8. Ese peso exige ejecutar el trabajo de generación de forma asíncrona.

La representación no debe cambiar la redirección existente ni contar como un clic. También debe
respetar la caché de redirecciones y el estado de una URL administrable, para conservar los `404`
y `410` que ya expone `links`.

## Decisión

1. Usaremos ZXing (`core` y `javase`) para codificar únicamente PNG. No se ofrecerán SVG ni
   códigos QR Base64 en las respuestas de creación.
2. El módulo `links` generará la imagen bajo demanda en un caso de uso asíncrono con `@Async` y
   `CompletableFuture`. La imagen no se guardará en PostgreSQL, Redis ni memoria entre
   peticiones.
3. El adaptador web servirá el PNG solo para `GET /{hash}` con `Accept: image/png` y declarará
   `image/png` como representación producida. La redirección normal conservará su ruta y su
   respuesta `307` cuando no se solicite esa representación.
4. La validación del hash reutilizará una única resolución activa de `links`, incluida la caché de
   redirecciones. Solo la operación de redirección publicará `ClickLoggedEvent`; pedir un QR no
   registrará un clic.

## Consecuencias

- La codificación no bloquea el hilo MVC que recibe la petición, cumpliendo el nivel 2 asociado
  al peso 8.
- No hay estado nuevo que sincronizar entre las dos réplicas de Compose.
- Se añade la dependencia especializada ZXing y un adaptador secundario de codificación, por lo
  que deben mantenerse los estereotipos hexagonales y `ModularityTests` en verde.
- Las pruebas HTTP deberán comprobar los cinco criterios de la ficha y, adicionalmente, que un
  QR no publica un evento de clic.

## Alternativas consideradas

| Opción | Por qué se descarta |
| --- | --- |
| Generar el PNG de forma síncrona en el controlador | No cumple el nivel asíncrono exigido por el peso 8. |
| Guardar o cachear los PNG | Añade invalidación y estado compartido sin necesidad para una imagen pequeña generada bajo demanda. |
| Incluir Base64 en `POST /api/link` | Cambia una respuesta de creación existente y puede entrar en conflicto con otras features. |
| Ofrecer SVG además de PNG | Amplía el contrato sin aportar nada a los criterios de aceptación. |
| Publicar un evento de clic al pedir el QR | Generar una representación no es una redirección y alteraría las estadísticas de la 0004. |

## Evidencias (defensa)

- [x] Código: `links.application.QrCodeService`, `links.adapters.qr.ZxingQrCodeEncoder` y
  `links.adapters.web.LinkController`; ficha [`0007`](../features/0007-qr-code-generator.md).
- [x] Pruebas: `QrCodeFlowTests`, `QrCodeServiceTests`, `ModularityTests` y `./gradlew check`.
- [x] Demostración: `./gradlew bootJar --rerun-tasks`, `docker compose up --build` y
  `GET /1001` con `Accept: image/png` a través de Nginx devolvió un PNG `200` de 256 × 256.
