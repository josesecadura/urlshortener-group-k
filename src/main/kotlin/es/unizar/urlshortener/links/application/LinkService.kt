package es.unizar.urlshortener.links.application

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import es.unizar.urlshortener.links.UrlExpirationEvent
import es.unizar.urlshortener.links.domain.ShortUrl
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class LinkService(
    private val store: ShortUrlStore,
    private val validator: UrlValidator,
    private val codes: ShortCodeSource,
    private val cache: RedirectCache,
    private val events: ApplicationEventPublisher,
) : CreateShortUrl,
    RedirectShortUrl,
    ExpiredURL {
    @Transactional
    override fun create(
        url: String,
        expirationTime: Instant?,
    ): CreatedShortUrl {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) { "url must not be blank" }
        if (!validator.isValid(trimmed)) {
            throw InvalidUrlException(trimmed)
        }
        val stored = store.insert(ShortUrl(hash = codes.next(), target = trimmed, expirationTime = expirationTime))
        events.publishEvent(ShortUrlCreatedEvent(hash = stored.hash, target = stored.target))
        return CreatedShortUrl(hash = stored.hash, target = stored.target)
    }

    @Transactional
    override fun redirect(hash: String): Redirection {
        // 1) caché; 2) si falla, BD y se guarda en caché para la próxima
        val shortUrl =
            cache.get(hash)
                ?: store.findByHash(hash).orElseThrow { LinkNotFoundException(hash) }.also { cache.put(it) }
        if (shortUrl.expirationTime != null && Instant.now().isAfter(shortUrl.expirationTime)) {
            // url caducada, se elimina de BD. Publico evento para avisar de que lo he borrado
            events.publishEvent(UrlExpirationEvent(hash = hash, expirationTime = Instant.now()))
            store.delete(hash)
            // la URL ya no existe: avisa para invalidar la caché
            events.publishEvent(ShortUrlUpdatedEvent(hash = hash))
            throw UrlExpiredException(hash)
        }
        events.publishEvent(ClickLoggedEvent(hash = hash, occurredAt = Instant.now()))
        return Redirection(target = shortUrl.target)
    }

    @Transactional
    override fun deleteExpiredURL(now: Instant): Int {
        val listDelete = store.getExpiredURLPeriodic(now)
        for (h in listDelete) {
            // se publica el evento para cada URL que ha caducado
            events.publishEvent(UrlExpirationEvent(hash = h, expirationTime = now))
            events.publishEvent(ShortUrlUpdatedEvent(hash = h))
        }
        return store.deleteExpiredURLPeriodic(Instant.now())
    }
}
