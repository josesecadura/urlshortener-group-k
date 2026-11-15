package es.unizar.urlshortener.links.application

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.links.UrlExpirationEvent
import es.unizar.urlshortener.links.domain.ShortUrl
import jakarta.validation.constraints.Future
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class LinkService(
    private val store: ShortUrlStore,
    private val validator: UrlValidator,
    private val codes: ShortCodeSource,
    private val events: ApplicationEventPublisher,
) : CreateShortUrl,
    RedirectShortUrl,
    ExpiredURL {
    @Transactional
    override fun create(
        url: String,
        @Future expirationTime: Instant?,   // se indica con Future para que solo se admita si la fecha es futura, si se introduce una fecha antigua devuelve 400 Bad Request
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
    //en transactional: Si se lanza excepcion, no se hace commit de la BD. En caso de que la excepcion sea UrlExpiredException queremos que se haga commit para eliminar la url caducada -> norollbackfor
    @Transactional(noRollbackForClassName = ["UrlExpiredException"])
    override fun redirect(hash: String): Redirection {
        val shortUrl = store.findByHash(hash).orElseThrow { LinkNotFoundException(hash) }
        if (shortUrl.expirationTime != null && Instant.now().isAfter(shortUrl.expirationTime)) {
            // url caducada, se elimina de BD. Publico evento para avisar de que lo he borrado
            events.publishEvent(UrlExpirationEvent(hash = hash, expirationTime = shortUrl.expirationTime))
            store.delete(hash)
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
        }
        return store.deleteExpiredURLPeriodic(Instant.now())
    }
}
