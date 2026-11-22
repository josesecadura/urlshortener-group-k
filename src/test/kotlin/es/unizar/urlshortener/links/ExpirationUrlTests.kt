package es.unizar.urlshortener.links

import com.jayway.jsonpath.JsonPath.read
import es.unizar.urlshortener.analytics.application.GetLinkStats
import es.unizar.urlshortener.links.application.CreateShortUrl
import es.unizar.urlshortener.links.application.ExpiredURL
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ExpirationUrlTests {
    @Autowired
    private lateinit var createShortUrl: CreateShortUrl

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
        // creo url con fecha de expiracion hace 1 año
        val url = "https://example.com/${UUID.randomUUID()}"

        val create =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", url)
                        .param("expirationTime", Instant.now().minus(365, ChronoUnit.DAYS).toString()),
                ).andExpect(status().isBadRequest)
    }

    @Test
    fun `crear url y comprobar redirect funciona`() {
        // creo url con fecha de expiracion dentro de 1 año
        val url = "https://example.com/${UUID.randomUUID()}"

        val create =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", url)
                        .param("expirationTime", Instant.now().plus(365, ChronoUnit.DAYS).toString()),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.hash").isString)
                .andReturn()

        val hash = read<String>(create.response.contentAsString, "$.hash")
        // se intenta acceder a esa url con el hash devuelto al crearla
        // se comprueba que devuelve redireccion
        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", url))
    }

    @Test
    fun `crear url caduca en diez segundos y comprobar redirect devuelve expirada`() {
        // creo url con fecha de expiracion dentro de 1 año
        val url = "https://example.com/${UUID.randomUUID()}"

        val create =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", url)
                        .param("expirationTime", Instant.now().plus(10, ChronoUnit.SECONDS).toString()),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.hash").isString)
                .andReturn()

        val hash = read<String>(create.response.contentAsString, "$.hash")
        // se intenta acceder a esa url con el hash devuelto al crearla
        // se comprueba que devuelve gone, porque la url ha expirado
        Thread.sleep(10 * 1000) // se para la ejecucion 10 segundos para esperar a que caduque
        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isGone)
            .andExpect(header().doesNotExist("Location"))

        // compruebo que se ha eliminado de la BD al haber caducado
        assertEquals(0, rowsFor(hash))
    }

    @Test
    fun `scheduled elimina url caducadas`() {
        // creo 2 url que caducan en 10 segundos, espero 10 segundos, ejecuto scheduled tasks y compruebo que se han borrado de BD
        val url = "https://example.com/${UUID.randomUUID()}"
        val create =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", url)
                        .param("expirationTime", Instant.now().plus(10, ChronoUnit.SECONDS).toString()),
                ).andExpect(status().isCreated)
                .andReturn()
        val hash = read<String>(create.response.contentAsString, "$.hash")

        val url2 = "https://example.com/${UUID.randomUUID()}"

        val create2 =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", url2)
                        .param("expirationTime", Instant.now().plus(10, ChronoUnit.SECONDS).toString()),
                ).andExpect(status().isCreated)
                .andReturn()
        val hash2 = read<String>(create2.response.contentAsString, "$.hash")

        Thread.sleep(10 * 1000) // se para la ejecucion 10 segundos para esperar a que caduque

        assertEquals(1, rowsFor(hash))
        assertEquals(1, rowsFor(hash2))
        // se podria plantear hacer el test llamando a la task del scheduler, pero la tarea se ejecuta nada mas arrancar y esta puesto
        // que como minimo el Lock permanece activo 1 munuto (lockAtLeastFor), habria que esperar 1 minuto a realizar la llamada

        assertEquals(2, expiredURL.deleteExpiredURL(Instant.now()))
        assertEquals(0, rowsFor(hash))
        assertEquals(0, rowsFor(hash2))
    }

    @Test
    fun `crear url, caduca, comprobar analyhics la ha borrado`() {
        // creo 1 url que caducan en 10 segundos, espero 10 segundos, compruebo si modulo analythics lo ha procesado.
        // Y si al eliminar tambien lo procesa y borra
        val url = "https://example.com/${UUID.randomUUID()}"
        val create =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", url)
                        .param("expirationTime", Instant.now().plus(10, ChronoUnit.SECONDS).toString()),
                ).andExpect(status().isCreated)
                .andReturn()
        val hash = read<String>(create.response.contentAsString, "$.hash")

        val redirect =
            mockMvc
                .perform(
                    get("/$hash"),
                ).andReturn()
        // el procesamiento del evento es asincrono, mockMVC continua en cuanto el controlador responde, no espera a que termine.
        // Por tanto, tengo que esperar un poco hasta que se consuma el evento y se incremente totalClicks
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val linkStats = getLinkStats.findByHash(hash)
            assertNotNull(linkStats)
            assertEquals(1, linkStats.totalClicks)
        }

        Thread.sleep(10 * 1000) // se para la ejecucion 10 segundos para esperar a que caduque

        assertEquals(1, rowsFor(hash))

        assertEquals(1, expiredURL.deleteExpiredURL(Instant.now()))
        assertEquals(0, rowsFor(hash))

        // url ha caducado, si intento obtener estadisticas deberia no tener ninguna. Me devuelve null

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertNull(getLinkStats.findByHash(hash))
        }
    }

    private fun rowsFor(hash: String): Int =
        jdbc.queryForObject("select count(*) from short_url where hash = ?", Int::class.java, hash) ?: 0
}
