package es.unizar.urlshortener.links.adapters.messageBroker

import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import es.unizar.urlshortener.links.application.InvalidateRedirectCache
import org.jmolecules.architecture.hexagonal.PrimaryAdapter
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@PrimaryAdapter
@Component
@Profile("broker")
class RedirectCacheBroker(
    private val invalidateRedirectCache: InvalidateRedirectCache,
) {
    @RabbitListener(id = "redirect-cache-invalidation", queues = ["\${urlshortener.redirect-cache.broker.queue}"])
    fun on(event: ShortUrlUpdatedEvent) {
        invalidateRedirectCache.invalidate(event.hash)
    }
}
