package es.unizar.urlshortener.clicks

import es.unizar.urlshortener.RabbitMqContainer
import es.unizar.urlshortener.links.application.CreateShortUrl
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Testcontainers(disabledWithoutDocker = true)
@Import(RabbitMqContainer::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("broker")
class RabbitmqTest {
    @Autowired
    lateinit var eventsRabbit: RabbitMQContainer

    @Autowired
    lateinit var createShortUrl: CreateShortUrl

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var client: TestRestTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var incomplete: IncompleteEventPublications

    @Test
    fun `broker down returns 307`() {
        // 307 es el codigo que devuelve http tras hacer una llamada a redirect con una url, como que la redireccion es correcta
        // aunque el broker este caido tiene que hacer la redireccion correctamente
        // el broker se utiliza para el conteo de los clicks, mientras este caido el contador de clicks no se incrementara en la BD

        eventsRabbit.stop()

        val url = "https://example.com/${UUID.randomUUID()}"
        val hash = createShortUrl.create(url).hash
        // se crea el cliente http indicando que no se van a realizar redirecciones, para que devuelve 307
        val clienthttp = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

        val response =
            clienthttp.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/$hash")).GET().build(),
                HttpResponse.BodyHandlers.discarding(),
            )

        // se comprueba que con el broker en stop (simila broker caido), se sigue recibiendo 307 (redireccion)
        // print("Resuesta: "+response.statusCode())
        assertEquals(307, response.statusCode())

        // se comprueba que devuelve la misma URL que hemos creado
        assertEquals(url, response.headers().firstValue("Location").getOrNull())

        // comprobamos si el broker es resistente a caidas
        assertEquals(0, rowsFor(hash)) // el broker esta caido, por tanto no se registra el click

        eventsRabbit.start() // se vuelve a iniciar el broker
        incomplete.resubmitIncompletePublications { true } // se reenvian todos los eventos pendientes
        assertEquals(true, eventsRabbit.isRunning)
        // se comprueba que se ha recuperado el evento pendiente, se ha recibido y se ha contabilizado el click

        // el Rabbit listener intenta conectarse al broker caido aproximadamente cada 5 segundos, asi le damos tiempo para que se conecte
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertEquals(1, rowsFor(hash))
        }
        // si pasa el test se comprueba como los mensajes que no se han entregado porque el broker estaba caido, se almacenan en Outbox y
        // se reentregan cuando el broker vuelve a conectarse (almacenandose 1 sola vez en la BD, idempotencia)
    }

    private fun rowsFor(hash: String): Int = jdbc.queryForObject("select count(*) from click where hash = ?", Int::class.java, hash) ?: 0
}
