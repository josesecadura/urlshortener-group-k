package es.unizar.urlshortener.clicks.adapters.events

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.clicks.ClickLoggedIngestionEvent
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
@Profile("broker")
class ClickForwarder(
    private var eventsRabbit: RabbitTemplate,
    private var eventos: ApplicationEventPublisher,
) {
    @ApplicationModuleListener
    fun on(event: ClickLoggedEvent) {
        // publica un evento en rabbitmq en el exchange que se indica
        eventsRabbit.convertAndSend("clicks.ingest", "click", ClickLoggedIngestionEvent(event.hash, event.occurredAt, event.eventId))
        /*// lo publico tambien como evento, para que lo pueda leer en clickIngestion
        eventos.publishEvent(ClickLoggedIngestionEvent(event.hash, event.occurredAt, event.eventId))
*/
    }
}
