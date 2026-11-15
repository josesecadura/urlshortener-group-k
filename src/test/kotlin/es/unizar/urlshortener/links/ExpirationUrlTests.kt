package es.unizar.urlshortener.links

import es.unizar.urlshortener.links.application.CreateShortUrl
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import java.util.UUID
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.jayway.jsonpath.JsonPath.read
import es.unizar.urlshortener.links.application.ExpiredURL
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.jdbc.core.JdbcTemplate

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
        //se intenta acceder a esa url con el hash devuelto al crearla
        //se comprueba que devuelve redireccion
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
        //se intenta acceder a esa url con el hash devuelto al crearla
        //se comprueba que devuelve gone, porque la url ha expirado
        Thread.sleep(10*1000)   //se para la ejecucion 10 segundos para esperar a que caduque
        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isGone)
            .andExpect(header().doesNotExist("Location"))

        //compruebo que se ha eliminado de la BD al haber caducado
        assertEquals(0,rowsFor(hash))


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
                .andExpect(jsonPath("$.hash").isString)
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
                .andExpect(jsonPath("$.hash").isString)
                .andReturn()
        val hash2 = read<String>(create2.response.contentAsString, "$.hash")

        Thread.sleep(10*1000)   //se para la ejecucion 10 segundos para esperar a que caduque

        assertEquals(1,rowsFor(hash))
        assertEquals(1,rowsFor(hash2))
        val eliminados=expiredURL.deleteExpiredURL(Instant.now())
        //se podria plantear hacer el test llamando a la task del scheduler, pero esta tarea se ejecuta nada mas arrancar y esta establecido que como cinimo el Lock permanece activo 1 munuto (lockAtLeastFor), de esta forma habria que esperar 1 minuto a realizar la llamada

        assertEquals(2, eliminados)
        assertEquals(0,rowsFor(hash))
        assertEquals(0,rowsFor(hash2))






    }

    private fun rowsFor(hash: String): Int = jdbc.queryForObject("select count(*) from short_url where hash = ?", Int::class.java, hash) ?: 0

}
