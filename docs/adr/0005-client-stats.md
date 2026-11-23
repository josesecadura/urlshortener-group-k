# ADR 0005 · Estadísticas por navegador y plataforma

- **Estado** · Se ha adoptado esta decisión
- **Fecha** · 21 de noviembre de 2026
- **Módulo responsable** · `analytics`

## Qué se necesita

Se quiere saber cuántos clics recibe un enlace desde cada navegador y sistema operativo. El `User-Agent` también puede incluir la versión, el modelo del dispositivo y otros datos que sobran para hacer ese recuento.

## Cómo se obtiene el cliente

Se analiza la cabecera en el controlador de redirección con `uap-java`. Se reutilizan sus reglas para reconocer navegadores y sistemas. Se envían las dos familias en el `ClickLoggedEvent` solo si se puede redirigir el enlace. Así se evita guardar la cabecera incluso en la tabla que utiliza Spring Modulith para reenviar eventos pendientes.

Si falta la cabecera se usa `Unknown` para ambas familias. Si se reconoce una sola familia se conserva ese dato y se usa `Unknown` para la otra. Si se recibe un evento antiguo sin estos campos se aplican los mismos valores por defecto.

## Cómo se cuenta

Se guarda una fila en `client_count` por cada combinación de enlace, navegador y plataforma. Cuando llega otro clic de la misma pareja se incrementa la fila con el mismo tipo de operación que ya se utiliza para el total de clics. Se comprueba el `eventId` antes de actualizar ambos contadores y se hacen las dos escrituras dentro de la misma transacción. Se mantiene el plazo de conservación de identificadores definido en el [ADR 0002](0002-atomic-upserts.md).

Al crear un enlace se prepara su fila de estadísticas dentro de la transacción de creación. Así se puede consultar el enlace sin clics en cuanto se recibe el `201`. Si falla esa escritura también se revierte la creación del enlace. Los clics se siguen procesando de forma asíncrona.

Para consultar las estadísticas se leen el total y las parejas de un enlace en una sola consulta. Se recorre el resultado y se suma cada contador en los dos mapas. Si el total incluye clics previos a la detección, se añade la diferencia a `Unknown` en ambos mapas.

## Otras opciones que se valoraron

- Con reglas escritas a mano habría que mantener los patrones al aparecer nuevos navegadores
- Si se agrupara todo el historial en cada consulta, se leerían de nuevo muchos clics de la misma pareja
- Si se guardaran los contadores en memoria, cada réplica tendría solo una parte del recuento y se perderían los datos al reiniciar

## Cómo se comprueba

Se comprueban los códigos HTTP, los mapas, los datos antiguos y la ausencia de la cabecera original en `ClientStatsFlowTests`. En `LinkStatsConcurrencyTests` se comprueba qué ocurre con varios clics simultáneos y con varias entregas del mismo evento.
