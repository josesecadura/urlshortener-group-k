# ADR 0006: Caché de redirección invalidada por `ShortUrlUpdatedEvent`

- **Estado:** Aceptado (prueba de concepto)
- **Fecha:** 2026-10-23
- **Responsables:** Grupo k / ficha [`0006`](../features/0006-redirect-cache-with-cross-replica.md)


## Contexto

Cada `GET /{hash}` lee el destino de la base de datos. Queremos servir las redirecciones repetidas desde una caché y eliminar la entrada en cuanto la URL deje de ser válida (borrada o caducada), para que ningún cliente reciba un destino obsoleto.

## Decisión de la prueba de concepto

1. **Módulos:** solo interviene `links`. La caché pertenece a datos que `links` ya  tiene por lo que publica y consume el evento. No se toca ningún otro módulo y al lanzar el test `./gradlew test --tests '*ModularityTests'` sigue pasando.
2. **Evento:** nuevo `ShortUrlUpdatedEvent(hash, updatedAt)` en el paquete base de `links`. `LinkService` lo publica al borrar una URL caducada (`redirect` sobre un enlace caducado y `deleteExpiredURL`).
3. **Puerto y adaptadores (hexágono):**
   - `RedirectCache` (puerto secundario, `links.application`): `get` / `put` / `evict`.
   - `InMemoryRedirectCache` (adaptador secundario, `links.adapters.cache`): `ConcurrentHashMap` de `hash -> ShortUrl`.
   - `RedirectCacheInvalidator` (adaptador primario, `links.adapters.events`): `@EventListener` síncrono que llama a `evict(hash)` al recibir `ShortUrlUpdatedEvent`.
4. **Flujo:** `LinkService.redirect` consulta primero la caché, si falla lo lee de la BD rellena. La comprobación de si el link ha caducado se hace después de leer la caché, así que una entrada cacheada pero caducada sigue rechazándose (410).
5. **No se añade ninguna librería** en esta prueba de concepto.

## Consecuencias de la prueba de concepto

- Las redirecciones repetidas no consultan la BD. Un evento de actualización/borrado vacía la entrada.
- Ahora mismo la caché es `local` a cada réplica. Con `app1` + `app2` tras nginx, un evento publicado en una réplica no llega a la otra.
- Para siguientes pasos lo que haremos será sustituir `InMemoryRedirectCache` por Redis (caché compartida + `EXPIRE`) y propagar la invalidación.
- El listener es síncrono para que la entrada ya no exista cuando `publishEvent` devuelve. Un listener asíncrono obligaría al test a esperar.

## Integración con Redis y RabbitMQ

- `links` mantiene el puerto `RedirectCache` y publica `ShortUrlUpdatedEvent`. El adaptador Redis comparte las entradas entre réplicas. El listener local borra la clave después del commit aunque RabbitMQ no esté disponible.
- El módulo `cacheinvalidation` consume el evento público de `links` y lo envía como mensaje persistente a RabbitMQ. Reutiliza el registro JDBC de Spring Modulith y espera un ACK sin devolución para completar la publicación. Si falla, el registro permite reintentar el envío.
- Un consumidor de `links` borra la clave en Redis mediante el puerto de entrada `InvalidateRedirectCache`. Las réplicas comparten la misma cola pq borrar una clave afecta a ambas y repetir el borrado es seguro.
- El envío y el consumo AMQP se activan con el perfil `broker`.

## Alternativas consideradas

| Opción | Por qué se descarta |
| --- | --- |
| `@Cacheable` de Spring en proceso | Cada réplica tendría su propia caché, por lo que podrían quedar datos desactualizados. Además, no se usa el evento de invalidación que pide la ficha.|
| Solo expiración por TTL | La caché podría seguir teniendo datos antiguos hasta que expire el tiempo configurado, y eso no cumple lo que pide la ficha. |
| Redis + pub/sub | Reutilizamos RabbitMQ y el registro JDBC que ya usa la 0004 para conservar y reintentar las invalidaciones. |
| Publicar y consumir solo en `links` | El evento de invalidación se quedaría en el mismo módulo de Spring Modulith. |

## Evidencias (defensa)

- Test: `RedirectCacheTests`: la redirección rellena la caché y publicar `ShortUrlUpdatedEvent` la vacía.
- Comando: `./gradlew test --tests '*RedirectCacheTests'`
- Broker: `./gradlew test --tests '*RedirectCacheRabbitmqTests'` comprueba el commit, el rollback, el borrado en Redis y el caso sin ruta.
- Comprobar arquitectura: `./gradlew test --tests '*ModularityTests'`
