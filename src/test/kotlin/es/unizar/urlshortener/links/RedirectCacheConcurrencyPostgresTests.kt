
package es.unizar.urlshortener.links

import es.unizar.urlshortener.PostgresContainer
import es.unizar.urlshortener.RedisTestContainerConfiguration
import es.unizar.urlshortener.links.application.CreateManagedShortUrl
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.application.ShortUrlStore
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.SECONDS

@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresContainer::class, RedisTestContainerConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["urlshortener.redirect-cache.provider=redis"])
class RedirectCacheConcurrencyPostgresTests {
    @Autowired
    lateinit var createManaged: CreateManagedShortUrl

    @Autowired
    lateinit var store: ShortUrlStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoSpyBean
    lateinit var cache: RedirectCache

    @Test
    @Timeout(45)
    fun `la desactivacion espera al relleno de Redis y el enlace queda fuera de cache`() {
        val created = createManaged.createManaged("https://example.com/${UUID.randomUUID()}")
        val hash = created.hash
        val continuar = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            assertNull(cache.get(hash))
            val pidRedireccion = pausarRelleno(hash, continuar)

            // Lanzamos el GET y lo dejamos pausado antes de guardar en Redis
            val redireccion =
                executor.submit {
                    mockMvc
                        .perform(get("/$hash"))
                        .andExpect(status().isTemporaryRedirect)
                }

            val pid = pidRedireccion.get(10, SECONDS)

            // Intentamos desactivar la URL mientras el GET mantiene el bloqueo
            val desactivacion = executor.submit { desactivarPorHttp(hash, created.managementKey) }

            // Comprobamos que PostgreSQL no deja terminar la desactivacion todavia
            esperarBloqueo(pid, desactivacion)

            // Dejamos continuar el GET para que despues termine la desactivacion
            continuar.countDown()
            redireccion.get(10, SECONDS)
            desactivacion.get(10, SECONDS)

            // Redis debe quedar vacio y la URL ya no puede redirigir
            comprobarEnlaceDesactivado(hash)
        } finally {
            // Liberamos los hilos y limpiamos los datos aunque falle el test
            continuar.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, SECONDS), "Las dos peticiones deben terminar antes de limpiar")
            cache.evict(hash)
            store.delete(hash)
        }
    }

    private fun desactivarPorHttp(
        hash: String,
        managementKey: String,
    ) {
        mockMvc
            .perform(
                delete("/api/managed-links/$hash")
                    .header("Authorization", "Bearer $managementKey"),
            ).andExpect(status().isNoContent)
    }

    private fun comprobarEnlaceDesactivado(hash: String) {
        assertNull(cache.get(hash))
        repeat(2) {
            mockMvc
                .perform(get("/$hash"))
                .andExpect(status().isGone)
        }
        assertNull(cache.get(hash))
    }

    private fun pausarRelleno(
        hash: String,
        continuar: CountDownLatch,
    ): CompletableFuture<Int> {
        val url = store.findByHash(hash).orElseThrow()
        val pidRedireccion = CompletableFuture<Int>()

        // Paramos el guardado en Redis para intentar desactivar la URL al mismo tiempo
        doAnswer { invocation ->
            // Guardamos el PID de PostgreSQL para comprobar si hay un bloqueo
            pidRedireccion.complete(requireNotNull(jdbc.queryForObject("select pg_backend_pid()", Int::class.javaObjectType)))
            assertTrue(continuar.await(20, SECONDS), "El GET se ha quedado esperando para escribir en Redis")
            invocation.callRealMethod()
        }.`when`(cache).put(url)

        return pidRedireccion
    }

    // // Comprobamos que el DELETE espera por el bloqueo y no por otro motivo
    private fun esperarBloqueo(
        pid: Int,
        desactivacion: Future<*>,
    ) {
        await().atMost(Duration.ofSeconds(5)).until {
            assertFalse(desactivacion.isDone, "El DELETE ha terminado mientras el GET seguia pausado")
            jdbc.queryForObject(
                """
                select exists (
                    select 1 from pg_stat_activity
                    where query ilike '%update short_url%deactivated_at%'
                    and ? = any(pg_blocking_pids(pid))
                )
                """.trimIndent(),
                Boolean::class.javaObjectType,
                pid,
            ) == true
        }
    }
}
