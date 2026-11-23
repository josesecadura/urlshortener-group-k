package es.unizar.urlshortener

import com.jayway.jsonpath.JsonPath.read
import es.unizar.urlshortener.analytics.adapters.persistence.JpaLinkStatsRepository
import es.unizar.urlshortener.links.ShortUrlDeactivatedEvent
import es.unizar.urlshortener.links.adapters.persistence.JpaShortUrlRepository
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class UrlManagementFlowTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var stats: JpaLinkStatsRepository

    @Autowired
    lateinit var events: ApplicationEvents

    @Autowired
    lateinit var links: JpaShortUrlRepository

    private fun deactivations(hash: String) = events.stream(ShortUrlDeactivatedEvent::class.java).filter { it.hash == hash }.toList()

    @Test
    fun `deactivating a link stops redirects`() {
        val (hash, key) = createManagedLink()

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "https://example.com/managed"))

        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer $key"))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isGone)
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.status").value(410))

        // Repetir la desactivacion no debe dar error
        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer $key"))
            .andExpect(status().isNoContent)

        // Se cuentan los avisos de este enlace aunque se haya enviado el DELETE dos veces
        val published = deactivations(hash)
        assertEquals(1, published.size)

        // Se espera a que lleguen tanto el clic anterior como la desactivación
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val row = stats.findById(hash).orElse(null)
            assertEquals(1L, row?.totalClicks)
            // La base de datos guarda la fecha con menos precisión que Instant
            val date = row?.deactivatedAt
            assertTrue(date != null && Duration.between(published.single().deactivatedAt, date).abs() <= Duration.ofMillis(1))
        }
        mockMvc.perform(get("/api/stats/$hash")).andExpect(jsonPath("$.deactivatedAt").doesNotExist())
    }

    @Test
    fun `a wrong key cannot deactivate a link`() {
        val (hash, key) = createManagedLink()

        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer wrong-key"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)

        // Se comprueba el rechazo antes de usar la clave correcta
        assertEquals(0, deactivations(hash).size)
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val row = stats.findById(hash).orElse(null)
            assertEquals(1L, row?.totalClicks)
            assertNull(row?.deactivatedAt)
        }

        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer $key"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deactivation requires a key`() {
        val (hash, _) = createManagedLink()

        mockMvc
            .perform(delete("/api/managed-links/$hash"))
            .andExpect(status().isForbidden)

        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer "))
            .andExpect(status().isForbidden)

        // Se envía una clave con un esquema distinto de Bearer
        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Basic incorrecta"))
            .andExpect(status().isForbidden)

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
    }

    @Test
    fun `unknown link cannot be deactivated`() {
        mockMvc
            .perform(delete("/api/managed-links/nonexistent").header("Authorization", "Bearer some-key"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `managed link creation validates urls`() {
        val before = links.count()
        // Se prueban por separado los tres valores que no se pueden acortar
        for (url in listOf("no-es-una-url", "", "   ")) {
            mockMvc
                .perform(post("/api/managed-links").contentType(APPLICATION_FORM_URLENCODED).param("url", url))
                .andExpect(status().isBadRequest)
            assertEquals(before, links.count())
        }
    }

    @Test
    fun `each managed link has a different key`() {
        val first = createManagedLink()
        val second = createManagedLink()

        assertNotEquals(first.first, second.first)
        assertNotEquals(first.second, second.second)

        // Se calcula el resumen de cada clave para compararlo con lo que se guardó
        for ((hash, key) in listOf(first, second)) {
            val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            assertEquals(HexFormat.of().formatHex(digest), links.findById(hash).orElseThrow().managementKeyHash)
        }

        // Se intenta desactivar la primera URL con la clave válida de la segunda
        mockMvc
            .perform(delete("/api/managed-links/${first.first}").header("Authorization", "Bearer ${second.second}"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/${first.first}")).andExpect(status().isTemporaryRedirect)
    }

    @Test
    fun `a regular link cannot be deactivated with a management key`() {
        // Se crea el enlace desde la ruta que no entrega una clave de gestión
        val result =
            mockMvc
                .perform(post("/api/link").contentType(APPLICATION_FORM_URLENCODED).param("url", "https://example.com"))
                .andExpect(status().isCreated)
                .andReturn()
        val hash = read<String>(result.response.contentAsString, "$.hash")
        val (_, otherKey) = createManagedLink()

        // Se comprueba que el intento se rechaza y el enlace sigue funcionando
        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer $otherKey"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/$hash")).andExpect(status().isTemporaryRedirect).andExpect(header().string("Location", "https://example.com"))
    }

    // Se devuelve el hash junto con la clave para que cada prueba use su propio enlace
    private fun createManagedLink(): Pair<String, String> {
        val result =
            mockMvc
                .perform(
                    post("/api/managed-links")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", "https://example.com/managed"),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.hash").isString)
                .andExpect(jsonPath("$.managementKey").isString)
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()

        val hash = read<String>(result.response.contentAsString, "$.hash")
        val key = read<String>(result.response.contentAsString, "$.managementKey")
        // La cabecera y el cuerpo deben apuntar al mismo enlace corto
        val shortUrl = read<String>(result.response.contentAsString, "$.url")
        assertEquals("http://localhost/$hash", shortUrl)
        assertEquals(shortUrl, result.response.getHeader("Location"))
        assertTrue(key.isNotBlank())
        return Pair(hash, key)
    }
}
