# Feature: Browser and Platform Detection

## Decision

**Owner module**: `analytics` | **Event-coupled**: Y (`links` obtiene los datos del cliente y `analytics` consume `ClickLoggedEvent` para actualizar las estadísticas)

Se identifica el navegador y el sistema operativo a partir de la cabecera `User-Agent` de cada redirección. Esta información se envía mediante eventos al módulo `analytics`, donde se guardan los recuentos por navegador y plataforma. Las estadísticas se pueden consultar mediante un endpoint.

**Traps**: guardar el `User-Agent` completo puede afectar a la privacidad; identificar navegadores mediante expresiones regulares propias puede producir errores; calcular las estadísticas recorriendo todos los clics sería poco eficiente cuando aumente su número.

**Technology hints**: `uap-java` para identificar el navegador y el sistema operativo; `ClickLoggedEvent` para transmitir la información; Spring Data JPA y PostgreSQL para guardar los recuentos; `@ApplicationModuleListener` para procesar los eventos en `analytics`.

---

- **Weight:** 8
- **Event-coupled:** Y

## Later (after the 2 October agreement)

- **ADR:** [0005 · Estadísticas por navegador y plataforma](../adr/0005-client-stats.md)

### Acceptance criteria

- [x] Al hacer `GET /{hash}` de una URL activa con un `User-Agent` de Chrome en Windows, se devuelve `307` con `Location` igual a la URL original y se registra un clic con navegador `Chrome` y plataforma `Windows`
- [x] Al hacer dos redirecciones con Chrome en Windows y otra con Firefox en Linux para el mismo `hash`, `GET /api/stats/{hash}/clients` devuelve `200` con `browsers.Chrome = 2`, `browsers.Firefox = 1`, `platforms.Windows = 2` y `platforms.Linux = 1` una vez procesados los eventos
- [x] Al hacer `GET /{hash}` sin cabecera `User-Agent`, se mantiene la redirección `307` y el clic se contabiliza como `Unknown` tanto en navegador como en plataforma
- [x] Al hacer `GET /{hash}` con un `User-Agent` no reconocible, se mantiene la redirección `307` y el clic se contabiliza como `Unknown` sin devolver un error de servidor
- [x] `GET /api/stats/{hash}/clients` de una URL existente sin clics devuelve `200` con los objetos `browsers` y `platforms` vacíos
- [x] `GET /api/stats/{hash}/clients` de un `hash` inexistente devuelve `404` y no crea estadísticas para ese identificador
- [x] `GET /{hash}` de una URL inexistente devuelve `404` y no añade ningún clic a las estadísticas de navegadores o plataformas
- [x] Si se procesa dos veces un mismo `ClickLoggedEvent` con el mismo `eventId`, las estadísticas por navegador y plataforma solo aumentan una vez
- [x] El valor completo del `User-Agent` no se guarda en la base de datos; solo se almacenan los datos necesarios para las estadísticas agregadas

### Scale evidence

Se arrancaron dos procesos del JAR con PostgreSQL 16.9 compartido. Se creó una URL corta y se enviaron 40 peticiones de redirección, alternando las dos instancias y realizando hasta 8 peticiones simultáneas. Todas devolvieron `307` con el destino correcto.

Una vez procesados los eventos, se consultaron las estadísticas desde ambas instancias y se obtuvo el mismo resultado.

| Navegador | Windows | Linux | Unknown | Total |
| --- | ---: | ---: | ---: | ---: |
| Chrome | 10 | 10 | 0 | 20 |
| Firefox | 10 | 0 | 0 | 10 |
| Unknown | 0 | 0 | 10 | 10 |
| **Total** | **20** | **10** | **10** | **40** |

Se comprobó que los recuentos se conservan en PostgreSQL y que las dos réplicas consultan los mismos datos. También se comprobó que los eventos se procesan sin duplicar los clics registrados. Queda pendiente medir el rendimiento bajo una carga sostenida durante más tiempo.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | Se ejecutan `ClientStatsFlowTests` y `LinkStatsConcurrencyTests`. Se comprueban los 9 criterios de aceptación, incluyendo los navegadores desconocidos, la consulta de estadísticas y el procesamiento de eventos duplicados |
| **Scalability** | 3.5 | Se ejecutan dos instancias con PostgreSQL compartido y se envían 40 redirecciones con hasta 8 peticiones simultáneas. Se comprueba que ambas devuelven los mismos recuentos y que `analytics` procesa `ClickLoggedEvent` para actualizar las estadísticas |
| **Engineering** | 1.5 | Se ejecuta `./gradlew check` con JDK 25 para comprobar los tests, el estilo, el análisis estático, la cobertura y las pruebas de arquitectura. Se enlaza el ADR 0005 y se documenta el uso de IA |

**Indicative total:** 10 / 10

### AI disclosure

- **Tools / skills:** ChatGPT
- **Used for:** Se utilizó como apoyo durante la implementación para proponer parte del código, proponer algunos tests, revisar algunos errores y buscar soluciones a los problemas que se encontraron
- **Human-reviewed:** Se revisaron y adaptaron los cambios propuestos, se comprobó el funcionamiento de los endpoints y se ejecutaron las pruebas automáticas y las comprobaciones con dos instancias para verificar los resultados