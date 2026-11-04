package es.unizar.urlshortener.clicks.adapters.events

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.clicks.ClickLoggedIngestionEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
@Profile("broker")
class ClickForwarder(
    private var eventsRabbit: ApplicationEventPublisher,
) {
    @ApplicationModuleListener
    fun on(event: ClickLoggedEvent) {
        eventsRabbit.publishEvent(ClickLoggedIngestionEvent(event.hash, event.occurredAt, event.eventId))
    }
}
