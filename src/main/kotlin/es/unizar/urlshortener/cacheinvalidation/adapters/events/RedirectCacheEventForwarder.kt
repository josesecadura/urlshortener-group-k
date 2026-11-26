package es.unizar.urlshortener.cacheinvalidation.adapters.events

import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import org.jmolecules.architecture.hexagonal.PrimaryAdapter
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@PrimaryAdapter
@Component
@Profile("broker")
class RedirectCacheEventForwarder(
    private val rabbit: RabbitTemplate,
    @Value("\${urlshortener.redirect-cache.broker.exchange}")
    private val exchange: String,
    @Value("\${urlshortener.redirect-cache.broker.routing-key}")
    private val routingKey: String,
) {
    @ApplicationModuleListener(id = "redirect-cache.rabbitmq")
    fun on(event: ShortUrlUpdatedEvent) {
        val correlation = CorrelationData()
        rabbit.convertAndSend(
            exchange,
            routingKey,
            event,
            { message ->
                message.messageProperties.deliveryMode = MessageDeliveryMode.PERSISTENT
                message
            },
            correlation,
        )
        val confirm =
            try {
                correlation.future.get(5, TimeUnit.SECONDS)
            } catch (ex: TimeoutException) {
                throw IllegalStateException("RabbitMQ no confirmo la invalidacion de ${event.hash} a tiempo", ex)
            }

        // Un ACK puede llegar aunque el mensaje no tenga ruta
        // Si falla, Modulith lo deja pendiente
        correlation.returned?.let { error("RabbitMQ devolvio la invalidacion de ${event.hash}: ${it.replyText}") }
        check(confirm.isAck) { "RabbitMQ rechazo la invalidacion de ${event.hash}: ${confirm.reason}" }
    }
}
