package es.unizar.urlshortener.clicks

import es.unizar.urlshortener.RabbitMqContainer
import es.unizar.urlshortener.clicks.application.RecordClickIngestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@TestConfiguration
class RecorsClickIngestionConfigurationTest {
    private var colaNameTest: String = "clicks.ingest.queueTest"
    private var topicExchangeName: String = "clicks.ingest"

    @Bean
    fun queueTest(): Queue {
        return Queue(colaNameTest, true) // durable=true para que si se se reinicia, se mantenga la cola
    }

    @Bean
    fun binding_test(
        queueTest: Queue,
        topicExchangeName: TopicExchange,
    ): Binding = BindingBuilder.bind(queueTest).to(topicExchangeName).with("#")
}

@ActiveProfiles("broker")
@Testcontainers(disabledWithoutDocker = true)
@Import(RabbitMqContainer::class, RecorsClickIngestionConfigurationTest::class)
@SpringBootTest
class RecordClickIngestionTest {
    @Autowired
    lateinit var recordClickIng: RecordClickIngestion

    @Autowired
    lateinit var jdbc: JdbcTemplate

    // al iniciarse el sistema, el listener de ClickIngestionBroker tambien se inicia y no puedo tener 2 listeners contra una misma cola
    // creo una nueva cola sobre la que voy a leer los mensajes
    private var colaName: String = "clicks.ingest.queueTest"
    private var topicExchangeName: String = "clicks.ingest"

    @Autowired
    lateinit var rabbit: RabbitTemplate

    @Autowired
    lateinit var events: ApplicationEventPublisher

    // para que un evento se publique necesito hacerlo dentro de una transaccion
    @Autowired
    lateinit var transaction: TransactionTemplate

    var timeout = 5000L

    // este test comprueba que al publicar un evento del tipo ClickLoggedEvent (el que publica redirect), se publica en la cola del broker
    // un evento del tipo ClickLoggedIngestionEvent. Se publica solo 1 evento
    @Test
    fun `message clickIngestion is published in broker`() {
        // creo el evento y lo publico
        val event = ClickLoggedEvent(hash = "redelivery-${UUID.randomUUID()}")
        transaction.executeWithoutResult {
            events.publishEvent(event)
        } // se publica el evento. Se tiene que hacer dentro de una transaccion (sustituye al @Transactional de la funcion de LinkService)

        // como estamos usando el broker, se transforma en ClickLoggedIngestion event (en clickForwarder) y se publica en el broker rabbitmq

        val rec =
            rabbit.receiveAndConvert(
                colaName,
                timeout,
                ParameterizedTypeReference.forType<ClickLoggedIngestionEvent>(ClickLoggedIngestionEvent::class.java),
            )
        assertNotNull(rec) // se ha recibido mensaje
        assertEquals(event.hash, rec.hash) // comparamos que se ha recibido el mismo mensaje

        // se intenta recibir un segundo mensaje, no le tiene que llegar, no hay mas mensajes
        val rec2 = rabbit.receive(colaName, timeout)
        assertNull(rec2)
    }

    @Test
    fun `a clickIngestion is logged in jdbc once, consumidor idempotente`() {
        val event = ClickLoggedIngestionEvent(hash = "redelivery-${UUID.randomUUID()}")
        // llamo REDELIVERIES veces a la funcion recordIngestion que añade el evento a la BD. Esto simila que le
        // llegan REDELIVERIES veces el mismo mensaje desde rabbitmq
        repeat(REDELIVERIES) { recordClickIng.recordIngestion(event) }
        // compruebo que solo se ha añadido una vez, por que para cada evento recibido solo se añade en BD una vez (cada evento tiene
        // eventID distinto, se genera al crear el objeto ed la clase evento aleatoriamente)
        // asi compruebo que el consumidor es idempotente
        assertEquals(1, rowsFor(event.hash))
    }

    private fun rowsFor(hash: String): Int = jdbc.queryForObject("select count(*) from click where hash = ?", Int::class.java, hash) ?: 0

    private companion object {
        const val REDELIVERIES = 3
    }
}
