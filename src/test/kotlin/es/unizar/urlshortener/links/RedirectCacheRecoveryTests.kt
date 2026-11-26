package es.unizar.urlshortener.links

import es.unizar.urlshortener.PostgresContainer
import es.unizar.urlshortener.RabbitMqContainer
import es.unizar.urlshortener.RedisTestContainerConfiguration
import es.unizar.urlshortener.links.application.CreateManagedShortUrl
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.application.ShortUrlStore
import es.unizar.urlshortener.links.domain.ShortUrl
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import java.time.Duration
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("broker")
@Import(PostgresContainer::class, RabbitMqContainer::class, RedisTestContainerConfiguration::class)
@TestPropertySource(
    properties = [
        "urlshortener.redirect-cache.provider=redis",
        "urlshortener.events.retry-interval=PT5S",
    ],
)
class RedirectCacheRecoveryTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var createManaged: CreateManagedShortUrl

    @Autowired
    lateinit var store: ShortUrlStore

    @Autowired
    lateinit var cache: RedirectCache

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var rabbit: RabbitMQContainer

    @Test
    @Timeout(120)
    fun `el planificador recupera la invalidacion cuando vuelve RabbitMQ`() {
        val link = createManaged.createManaged("https://example.com/recuperacion")
        val hash = link.hash

        try {
            val original = store.findByHash(hash).orElseThrow()
            cache.put(original)
            assertEquals(original.target, cache.get(hash)?.target)

            val publicacionId = desactivarConRabbitCaido(original, link.managementKey)

            await().atMost(Duration.ofSeconds(75)).untilAsserted {
                val estado = jdbc.queryForObject("select status from event_publication where id = ?", String::class.java, publicacionId)
                assertEquals("COMPLETED", estado)
                assertNull(cache.get(hash))
            }
        } finally {
            cache.evict(hash)
            store.delete(hash)
        }
    }

    private fun desactivarConRabbitCaido(
        original: ShortUrl,
        managementKey: String,
    ): UUID {
        val hash = original.hash
        assertTrue(rabbitEscucha(), "RabbitMQ debe estar disponible antes de pararlo")
        ejecutarRabbit("stop_app")
        try {
            await().atMost(Duration.ofSeconds(10)).until { !rabbitEscucha() }

            mockMvc
                .perform(delete("/api/managed-links/$hash").header(AUTHORIZATION, "Bearer $managementKey"))
                .andExpect(status().isNoContent)
            assertNotNull(store.findByHash(hash).orElseThrow().deactivatedAt)
            assertNull(cache.get(hash))
            mockMvc.perform(get("/$hash")).andExpect(status().isGone)

            val id = await().atMost(Duration.ofSeconds(15)).until({ publicacionFallida(hash) }, { it != null })

            // Recuperamos la entrada antigua para comprobar que esta vez la borra el consumidor.
            cache.put(original)
            assertEquals(original.target, cache.get(hash)?.target)
            return requireNotNull(id)
        } finally {
            ejecutarRabbit("start_app")
        }
    }

    private fun publicacionFallida(hash: String): UUID? =
        jdbc
            .queryForList(
                """
                select id from event_publication
                where listener_id = 'redirect-cache.rabbitmq'
                  and status = 'FAILED'
                  and (cast(serialized_event as jsonb) ->> 'hash') = ?
                """.trimIndent(),
                UUID::class.java,
                hash,
            ).singleOrNull()

    private fun rabbitEscucha(): Boolean =
        rabbit.execInContainer("rabbitmq-diagnostics", "-q", "-t", "5", "check_port_listener", "5672").exitCode == 0

    private fun ejecutarRabbit(comando: String) {
        val resultado = rabbit.execInContainer("rabbitmqctl", "-t", "15", comando)
        assertEquals(0, resultado.exitCode, resultado.stderr)
    }
}
