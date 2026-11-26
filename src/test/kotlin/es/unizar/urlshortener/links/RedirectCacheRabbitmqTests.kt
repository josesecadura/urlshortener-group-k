package es.unizar.urlshortener.links

import es.unizar.urlshortener.PostgresContainer
import es.unizar.urlshortener.RabbitMqContainer
import es.unizar.urlshortener.RedisTestContainerConfiguration
import es.unizar.urlshortener.links.application.CreateManagedShortUrl
import es.unizar.urlshortener.links.application.DeactivateManagedShortUrl
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.application.ShortUrlStore
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("broker")
@Import(PostgresContainer::class, RabbitMqContainer::class, RedisTestContainerConfiguration::class)
@TestPropertySource(properties = ["urlshortener.redirect-cache.provider=redis"])
class RedirectCacheRabbitmqTests {
    @Autowired
    lateinit var createManaged: CreateManagedShortUrl

    @Autowired
    lateinit var deactivateManaged: DeactivateManagedShortUrl

    @Autowired
    lateinit var store: ShortUrlStore

    @Autowired
    lateinit var cache: RedirectCache

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var transaction: TransactionTemplate

    @Autowired
    lateinit var listeners: RabbitListenerEndpointRegistry

    @Autowired
    lateinit var admin: AmqpAdmin

    @Autowired
    lateinit var topology: Declarables

    @Autowired
    lateinit var mapper: JsonMapper

    @MockitoSpyBean
    lateinit var rabbit: RabbitTemplate

    private val hashes = mutableListOf<String>()

    @AfterEach
    fun limpiar() {
        hashes.forEach { hash ->
            cache.evict(hash)
            store.delete(hash)
        }
    }

    @Test
    fun `la invalidacion llega a la cola despues del commit`() {
        val link = crearEnlace()

        sinConsumidor {
            transaction.executeWithoutResult {
                deactivateManaged.deactivate(link.hash, link.managementKey)
                assertNull(rabbit.receive(QUEUE, ESPERA_COLA_VACIA))
            }

            val message = requireNotNull(rabbit.receive(QUEUE, 5_000)) { "La cola no recibio la invalidacion tras el commit" }
            val event = mapper.readValue(message.body, ShortUrlUpdatedEvent::class.java)
            assertEquals(link.hash, event.hash)
            assertEquals(MessageDeliveryMode.PERSISTENT, message.messageProperties.receivedDeliveryMode)
            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertEquals("COMPLETED", estadoPublicacion(link.hash))
            }
        }
    }

    @Test
    fun `un rollback deja la URL activa y no publica en RabbitMQ`() {
        val link = crearEnlace()

        sinConsumidor {
            transaction.executeWithoutResult { status ->
                deactivateManaged.deactivate(link.hash, link.managementKey)
                status.setRollbackOnly()
            }

            assertNull(rabbit.receive(QUEUE, ESPERA_COLA_VACIA))
            assertNull(estadoPublicacion(link.hash))
            assertNull(store.findByHash(link.hash).orElseThrow().deactivatedAt)
        }
    }

    @Test
    fun `el consumidor borra Redis al recibir el mensaje de RabbitMQ`() {
        val link = crearEnlace()
        cache.put(store.findByHash(link.hash).orElseThrow())
        assertNotNull(cache.get(link.hash))

        // Enviamos directamente a RabbitMQ para que el listener local no pueda borrar la clave.
        rabbit.convertAndSend(EXCHANGE, ROUTING_KEY, ShortUrlUpdatedEvent(link.hash))

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertNull(cache.get(link.hash))
        }
    }

    @Test
    fun `un mensaje sin ruta deja pendiente la publicacion en PostgreSQL`() {
        val link = crearEnlace()
        val binding = topology.getDeclarablesByType(Binding::class.java).single()
        admin.removeBinding(binding)

        try {
            deactivateManaged.deactivate(link.hash, link.managementKey)
            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertEquals("FAILED", estadoPublicacion(link.hash))
            }

            val correlation = correlacionDelEnvio(link.hash)
            assertTrue(correlation.future.get(1, TimeUnit.SECONDS).isAck)
            assertEquals("NO_ROUTE", correlation.returned?.replyText)
        } finally {
            admin.declareBinding(binding)
        }
    }

    private fun crearEnlace() = createManaged.createManaged("https://example.com/${UUID.randomUUID()}").also { hashes.add(it.hash) }

    private fun sinConsumidor(block: () -> Unit) {
        val listener = requireNotNull(listeners.getListenerContainer("redirect-cache-invalidation"))
        listener.stop()
        try {
            block()
        } finally {
            listener.start()
        }
    }

    private fun correlacionDelEnvio(hash: String): CorrelationData =
        mockingDetails(rabbit)
            .invocations
            .single {
                it.method.name == "convertAndSend" &&
                    (it.arguments.getOrNull(2) as? ShortUrlUpdatedEvent)?.hash == hash
            }.arguments
            .filterIsInstance<CorrelationData>()
            .single()

    private fun estadoPublicacion(hash: String): String? =
        jdbc
            .queryForList(
                """
                select status from event_publication
                where listener_id = ?
                  and (cast(serialized_event as jsonb) ->> 'hash') = ?
                """.trimIndent(),
                String::class.java,
                "redirect-cache.rabbitmq",
                hash,
            ).singleOrNull()

    private companion object {
        const val EXCHANGE = "links.cache-invalidation"
        const val QUEUE = "links.cache-invalidation.queue"
        const val ROUTING_KEY = "cache.invalidate"
        const val ESPERA_COLA_VACIA = 500L
    }
}
