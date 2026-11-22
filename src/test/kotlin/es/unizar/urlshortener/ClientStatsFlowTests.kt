package es.unizar.urlshortener

import com.jayway.jsonpath.JsonPath.read
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.json.JsonCompareMode.STRICT
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ClientStatsFlowTests {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var db: JdbcTemplate

    @Test
    fun `un enlace nuevo tiene los dos mapas vacios`() {
        // Se consulta justo después del 201 para comprobar que el enlace ya está disponible
        expectClients(createLink(), """{"browsers":{},"platforms":{}}""")
    }

    @Test
    fun `se agrupan los clics sin mezclar enlaces`() {
        val hash = createLink()
        val other = createLink()
        // Se repite la primera pareja y se añade otra distinta al mismo enlace
        redirect(hash, CHROME)
        redirect(hash, CHROME)
        redirect(hash, FIREFOX)
        redirect(other, FIREFOX)

        // Se espera a que analytics procese los eventos de las cuatro redirecciones
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            expectClients(hash, """{"browsers":{"Chrome":2,"Firefox":1},"platforms":{"Windows":2,"Linux":1}}""")
            expectClients(other, """{"browsers":{"Firefox":1},"platforms":{"Linux":1}}""")
        }
    }

    @Test
    fun `se cuentan las cabeceras desconocidas y se descarta el texto original`() {
        val hash = createLink()
        val marker = "cliente-de-prueba-${UUID.randomUUID()}"
        // Se envía primero una petición sin cabecera y luego otra que la librería no reconoce
        redirect(hash, null)
        redirect(hash, marker)
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            expectClients(hash, """{"browsers":{"Unknown":2},"platforms":{"Unknown":2}}""")
            assertEquals(2, db.queryForObject("select count(*) from click where hash = ?", Int::class.java, hash))
        }

        // Se buscan restos de la cabecera tanto en los eventos persistidos como en las filas del clic y del contador
        val sql = "select count(*) from event_publication where serialized_event like ?"
        val matches = db.queryForObject(sql, Int::class.java, "%$marker%")
        assertEquals(0, matches)
        val clicks = db.queryForList("select * from click where hash = ?", hash)
        val counts = db.queryForList("select * from client_count where hash = ?", hash)
        assertFalse((clicks + counts).any { row -> row.values.any { marker in it.toString() } })
    }

    @Test
    fun `consultar o redirigir un enlace inexistente no crea datos`() {
        val hash = "missing-${UUID.randomUUID()}"
        mvc.perform(get("/api/stats/$hash/clients")).andExpect(status().isNotFound)
        mvc.perform(get("/$hash").header(HttpHeaders.USER_AGENT, CHROME)).andExpect(status().isNotFound)
        // Se comprueba también el registro de eventos para detectar un clic que todavía esté pendiente
        val sql = "select count(*) from event_publication where serialized_event like ?"
        assertEquals(0, db.queryForObject(sql, Int::class.java, "%$hash%"))
        // Se comprueba que las dos peticiones han dejado las tablas sin filas para ese hash
        for (table in listOf("link_stats", "click", "client_count")) {
            assertEquals(0, db.queryForObject("select count(*) from $table where hash = ?", Int::class.java, hash))
        }
    }

    @Test
    fun `se conservan los clics anteriores a la deteccion`() {
        val hash = createLink()
        // Se reproduce un enlace antiguo que ya tenía tres clics y ninguna familia guardada
        db.update("update link_stats set total_clicks = 3 where hash = ?", hash)
        redirect(hash, CHROME)
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            expectClients(hash, """{"browsers":{"Chrome":1,"Unknown":3},"platforms":{"Windows":1,"Unknown":3}}""")
        }
    }

    private fun createLink(): String {
        val request = post("/api/link").contentType(APPLICATION_FORM_URLENCODED).param("url", TARGET)
        val result = mvc.perform(request).andExpect(status().isCreated).andReturn()
        return read(result.response.contentAsString, "$.hash")
    }

    private fun redirect(
        hash: String,
        userAgent: String?,
    ) {
        val request = get("/$hash")
        if (userAgent != null) request.header(HttpHeaders.USER_AGENT, userAgent)
        // Se comprueban el 307 y el destino original con cada tipo de cliente
        mvc.perform(request).andExpect(status().isTemporaryRedirect).andExpect(header().string(HttpHeaders.LOCATION, TARGET))
    }

    private fun expectClients(
        hash: String,
        expected: String,
    ) {
        mvc.perform(get("/api/stats/$hash/clients")).andExpect(status().isOk).andExpect(content().json(expected, STRICT))
    }

    private companion object {
        const val TARGET = "https://example.com/clients"
        const val CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
        const val FIREFOX = "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0"
    }
}
