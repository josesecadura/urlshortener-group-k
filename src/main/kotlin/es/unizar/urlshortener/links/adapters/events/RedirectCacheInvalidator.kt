package es.unizar.urlshortener.links.adapters.events

import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import es.unizar.urlshortener.links.application.RedirectCache
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class RedirectCacheInvalidator(
    private val cache: RedirectCache,
) {
    // Solo se invalida cuando la modificacion de la URL ya se ha confirmado
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ShortUrlUpdatedEvent) {
        cache.evict(event.hash)
    }
}
