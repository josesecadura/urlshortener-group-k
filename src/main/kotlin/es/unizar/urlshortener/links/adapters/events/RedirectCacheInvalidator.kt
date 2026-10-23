package es.unizar.urlshortener.links.adapters.events

import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import es.unizar.urlshortener.links.application.RedirectCache
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

// Consumidor del evento: cuando una URL cambia, la saca de cache
@Component
class RedirectCacheInvalidator(
    private val cache: RedirectCache,
) {
    // Sincrono, procesamos antes de continuar para invalidar la cache
    @EventListener
    fun on(event: ShortUrlUpdatedEvent) {
        cache.evict(event.hash)
    }
}
