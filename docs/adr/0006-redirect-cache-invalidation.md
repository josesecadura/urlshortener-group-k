# ADR 0006: Caché de redirección invalidada por `ShortUrlUpdatedEvent`

- **Estado:** Aceptado
- **Fecha inicial:** 2026-10-23
- **Última revisión:** 2026-11-26
- **Responsables:** Grupo k / ficha [`0006`](../features/0006-redirect-cache-with-cross-replica.md)


## Contexto

Cada `GET /{hash}` lee el destino de la base de datos. Queremos servir las redirecciones repetidas desde una caché y eliminar la entrada en cuanto la URL deje de ser válida (borrada o caducada), para que ningún cliente reciba un destino obsoleto.

## Prueba de concepto

En la primera versión, `links` publicaba y consumía `ShortUrlUpdatedEvent(hash, updatedAt)`. El puerto `RedirectCache` se implementaba con `InMemoryRedirectCache` y un `ConcurrentHashMap` por réplica. `RedirectCacheInvalidator` usaba un `@EventListener` síncrono que borraba la entrada cuando se publicaba el evento. Cada réplica podía reutilizar sus destinos en caché, pero el evento solo llegaba a la réplica que lo publicaba.

## Decisión final

1. **Redis compartido.** Las dos réplicas leen la misma clave `redirect:{hash}`. El TTL por defecto es de cinco minutos y se reduce si la URL caduca antes. [RedisRedirectCache.kt](../../src/main/kotlin/es/unizar/urlshortener/links/adapters/cache/RedisRedirectCache.kt)
2. **Bloqueo al rellenar la caché.** Si Redis está vacío para ese hash, `LinkService.redirect()` lee la fila con `PESSIMISTIC_WRITE` y mantiene el bloqueo hasta terminar la transacción. Así una desactivación concurrente espera a que termine el rellenado y después puede borrar esa entrada. [LinkService.kt](../../src/main/kotlin/es/unizar/urlshortener/links/application/LinkService.kt) · [JpaShortUrlRepository.kt](../../src/main/kotlin/es/unizar/urlshortener/links/adapters/persistence/JpaShortUrlRepository.kt)
3. **Invalidación tras el commit.** `links` publica su evento público `ShortUrlUpdatedEvent` dentro de la transacción. El listener `AFTER_COMMIT` borra la clave en Redis al confirmar PostgreSQL, aunque RabbitMQ esté caído. [RedirectCacheInvalidator.kt](../../src/main/kotlin/es/unizar/urlshortener/links/adapters/events/RedirectCacheInvalidator.kt)
4. **Envío a RabbitMQ.** El módulo `cacheinvalidation` recibe el evento de `links` después del commit. Envía un mensaje persistente con una confirmación correlacionada por publicación. El envío se completa al recibir un ACK y comprobar que el mensaje no fue devuelto. Un NACK, un mensaje sin ruta o un timeout mantienen la publicación pendiente en el registro JDBC de Spring Modulith. [RedirectCacheEventForwarder.kt](../../src/main/kotlin/es/unizar/urlshortener/cacheinvalidation/adapters/events/RedirectCacheEventForwarder.kt)
5. **Recuperación.** El planificador compartido con ShedLock revisa por defecto cada cinco minutos las publicaciones pendientes de más de 30 segundos. El registro permite recuperar la misma publicación al volver RabbitMQ. Spring Modulith también tiene habilitado el reenvío de pendientes al reiniciar la aplicación. [ScheduledTasks.kt](../../src/main/kotlin/es/unizar/urlshortener/links/scheduling/ScheduledTasks.kt) · [application.yaml](../../src/main/resources/application.yaml)
6. **Una cola para ambas réplicas.** El exchange `links.cache-invalidation` envía con la clave `cache.invalidate` a la cola durable `links.cache-invalidation.queue`. Cualquiera de las dos réplicas puede consumir el mensaje y borrar la clave compartida mediante `InvalidateRedirectCache`. Repetir el borrado es seguro. Compose activa `docker,broker` en ambas réplicas y guarda los datos de RabbitMQ en `rabbitdata`. [Configuración de la cola](../../src/main/kotlin/es/unizar/urlshortener/links/adapters/messageBroker/RedirectCacheBrokerConfiguration.kt) · [Consumidor](../../src/main/kotlin/es/unizar/urlshortener/links/adapters/messageBroker/RedirectCacheBroker.kt) · [docker-compose.yml](../../docker-compose.yml)

## Integración pendiente con la 0009

- En la rama actual, `redirect()` intenta borrar la URL caducada y lanza `UrlExpiredException`. La excepción revierte el borrado y la publicación. Al integrar la 0009 hay que conservar su corrección del rollback y mantener `ShortUrlUpdatedEvent`. Se comprobará que la petición que ejecuta el borrado devuelve `410` y las siguientes devuelven `404`.
- Al integrar el barrido de la 0009 hay que usar el mismo `now` para seleccionar y borrar. Así cada fila eliminada por el barrido queda incluida entre los hashes para los que se publica el evento. [LinkService.kt](../../src/main/kotlin/es/unizar/urlshortener/links/application/LinkService.kt)

## Alternativas consideradas

| Opción | Por qué se descarta |
| --- | --- |
| `@Cacheable` de Spring en proceso | Cada réplica tendría su propia caché, por lo que podrían quedar datos desactualizados. Además, no se usa el evento de invalidación que pide la ficha.|
| Solo expiración por TTL | La caché podría seguir teniendo datos antiguos hasta que expire el tiempo configurado, y eso no cumple lo que pide la ficha. |
| Redis + pub/sub | Reutilizamos RabbitMQ y el registro JDBC que ya usa la 0004 para conservar y reintentar las invalidaciones. |
| Publicar y consumir solo en `links` | El evento de invalidación se quedaría en el mismo módulo de Spring Modulith. |

## Evidencias (defensa)

- `RedisRedirectCacheTests` verifica el TTL, el Redis compartido y los casos HTTP de error. `RedirectCacheConcurrencyPostgresTests` fuerza el orden entre rellenado y desactivación para comprobar el bloqueo.
- `RedirectCacheRabbitmqTests` comprueba el envío tras commit, el rollback, el consumo real y el caso de un mensaje sin ruta. `RedirectCacheRecoveryTests` corta RabbitMQ durante una desactivación y durante un barrido. Comprueba que la misma publicación pasa de `FAILED` a `COMPLETED` y que el consumidor borra de Redis la entrada que se había vuelto a introducir.
- `ModularityTests` comprueba los límites de los módulos y genera el diagrama `build/spring-modulith-docs/components.puml`.

```bash
./gradlew test --tests '*RedisRedirectCacheTests' --tests '*RedirectCacheConcurrencyPostgresTests'
./gradlew test --tests '*RedirectCacheRabbitmqTests' --tests '*RedirectCacheRecoveryTests'
./gradlew test --tests '*ModularityTests'
./gradlew check --rerun-tasks
```

### Demo con dos réplicas

Arranca el producto con `./gradlew bootJar` y `docker compose up --build`. En otra terminal crea una URL, redirige desde `app1` y desactívala desde `app2`.

```bash
enlace=$(curl -fsS -d 'url=https://example.com' http://localhost:8080/api/managed-links)
hash=$(jq -r .hash <<< "$enlace")
clave=$(jq -r .managementKey <<< "$enlace")
docker compose exec -T app1 curl -si "http://localhost:8080/$hash"
docker compose exec -T redis redis-cli EXISTS "redirect:$hash"
docker compose exec -T app2 curl -si -X DELETE -H "Authorization: Bearer $clave" "http://localhost:8080/api/managed-links/$hash"
docker compose exec -T redis redis-cli EXISTS "redirect:$hash"
docker compose exec -T app1 curl -si "http://localhost:8080/$hash"
docker compose exec -T app2 curl -si "http://localhost:8080/$hash"
```

La primera redirección devuelve `307` y Redis pasa de tener la clave a borrarla tras el `204` de la desactivación. Los dos últimos GET devuelven `410`. Para comprobar la carga del producto usa `docker compose --profile load run --rm k6`.
