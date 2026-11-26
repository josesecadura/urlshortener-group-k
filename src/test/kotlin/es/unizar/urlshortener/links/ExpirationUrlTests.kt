package es.unizar.urlshortener.links

import com.jayway.jsonpath.JsonPath.read
import es.unizar.urlshortener.analytics.application.GetLinkStats
import es.unizar.urlshortener.links.application.ExpiredURL
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.http.MediaType.IMAGE_PNG
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ExpirationUrlTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var expiredURL: ExpiredURL

    @Autowired
    lateinit var getLinkStats: GetLinkStats

    @Test
    fun `crear con fecha antigua devuelve error`() {
        mockMvc
            .perform(
                post("/api/link")
                    .contentType(APPLICATION_FORM_URLENCODED)
                    .param("url", "https://example.com/${UUID.randomUUID()}")
                    .param("expirationTime", Instant.now().minus(365, ChronoUnit.DAYS).toString()),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `crear una URL futura permite redirigir`() {
        val url = "https://example.com/${UUID.randomUUID()}"
        val hash = createUrl(url)

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", url))
    }

    @Test
    fun `una redireccion de URL caducada confirma el borrado`() {
        val hash = createUrl("https://example.com/${UUID.randomUUID()}")
        expire(hash)

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isGone)
            .andExpect(header().doesNotExist("Location"))

        assertEquals(0, rowsFor(hash))
        mockMvc.perform(get("/$hash")).andExpect(status().isNotFound)
    }

    @Test
    fun `una peticion QR de URL caducada confirma el borrado`() {
        val hash = createUrl("https://example.com/${UUID.randomUUID()}")
        expire(hash)

        mockMvc
            .perform(get("/$hash").accept(IMAGE_PNG))
            .andExpect(status().isGone)

        assertEquals(0, rowsFor(hash))
        mockMvc.perform(get("/$hash").accept(IMAGE_PNG)).andExpect(status().isNotFound)
    }

    @Test
    fun `el barrido elimina las URL caducadas`() {
        val first = createUrl("https://example.com/${UUID.randomUUID()}")
        val second = createUrl("https://example.com/${UUID.randomUUID()}")
        expire(first)
        expire(second)

        assertEquals(2, expiredURL.deleteExpiredURL(Instant.now()))
        assertEquals(0, rowsFor(first))
        assertEquals(0, rowsFor(second))
    }

    @Test
    fun `la caducidad elimina las estadisticas de la URL`() {
        val hash = createUrl("https://example.com/${UUID.randomUUID()}")

        mockMvc.perform(get("/$hash")).andExpect(status().isTemporaryRedirect)
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val stats = getLinkStats.findByHash(hash)
            assertNotNull(stats)
            assertEquals(1, requireNotNull(stats).totalClicks)
        }

        expire(hash)
        assertEquals(1, expiredURL.deleteExpiredURL(Instant.now()))
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertNull(getLinkStats.findByHash(hash))
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from client_count where hash = ?", Int::class.java, hash))
    }

    private fun createUrl(url: String): String {
        val created =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", url)
                        .param("expirationTime", Instant.now().plus(365, ChronoUnit.DAYS).toString()),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.hash").isString)
                .andReturn()

        return read(created.response.contentAsString, "$.hash")
    }

    private fun expire(hash: String) {
        val updated =
            jdbc.update(
                "update short_url set expiration_time = ? where hash = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                hash,
            )
        assertEquals(1, updated)
    }

    private fun rowsFor(hash: String): Int =
        jdbc.queryForObject("select count(*) from short_url where hash = ?", Int::class.java, hash) ?: 0
}
