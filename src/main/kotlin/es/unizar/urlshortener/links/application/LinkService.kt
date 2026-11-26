package es.unizar.urlshortener.links.application

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.links.ShortUrlDeactivatedEvent
import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
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
    private val cache: RedirectCache,
    private val events: ApplicationEventPublisher,
) : CreateShortUrl,
    CreateManagedShortUrl,
    DeactivateManagedShortUrl,
    InvalidateRedirectCache,
    RedirectShortUrl,
    ResolveShortUrl,
    ExpiredURL {
    @Transactional
    override fun create(
        url: String,
        @Future expirationTime: Instant?,
        // se indica con Future para que solo se admita si la fecha es futura, si se introduce una fecha antigua devuelve 400 Bad Request
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

    // Separamos esta creacion para no cambiar el comportamiento del endpoint original
    @Transactional
    override fun createManaged(url: String): ManagedShortUrlCreated {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) { "url must not be blank" }
        if (!validator.isValid(trimmed)) {
            throw InvalidUrlException(trimmed)
        }
        // Devolvemos la clave al usuario, pero solo guardamos su hash en la BD
        val key = ManagementKeys.issue()
        val stored =
            store.insert(
                ShortUrl(
                    hash = codes.next(),
                    target = trimmed,
                    managementKeyHash = key.sha256,
                ),
            )
        events.publishEvent(ShortUrlCreatedEvent(hash = stored.hash, target = stored.target))
        return ManagedShortUrlCreated(
            hash = stored.hash,
            target = stored.target,
            managementKey = key.plaintext,
        )
    }

    @Transactional
    override fun deactivate(
        hash: String,
        managementKey: String,
    ) {
        val existing = store.findByHash(hash).orElseThrow { LinkNotFoundException(hash) }
        val expectedDigest = existing.managementKeyHash
        if (expectedDigest == null || !ManagementKeys.matches(expectedDigest, managementKey)) {
            throw InvalidManagementKeyException()
        }
        // Si ya se desactivo, no hay que volver a modificarla
        if (existing.deactivatedAt != null) return

        val deactivatedAt = Instant.now()
        val updated = store.deactivate(hash, expectedDigest, deactivatedAt)
        // Solo avisamos a otros modulos si la URL se ha desactivado
        if (updated == 1) {
            events.publishEvent(ShortUrlDeactivatedEvent(hash = hash, deactivatedAt = deactivatedAt))
            events.publishEvent(ShortUrlUpdatedEvent(hash = hash))
        }
    }

    override fun invalidate(hash: String) = cache.evict(hash)

    @Transactional(noRollbackFor = [UrlExpiredException::class])
    override fun redirect(hash: String): Redirection {
        val redirection = resolve(hash)
        events.publishEvent(ClickLoggedEvent(hash = hash, occurredAt = Instant.now()))
        return redirection
    }

    @Transactional(noRollbackFor = [UrlExpiredException::class])
    override fun resolve(hash: String): Redirection {
        // 1) caché; 2) si falla, BD y se guarda en caché para la próxima
        // Bloqueamos la fila pq otra replica podria desactivar la URL antes de guardarla en Redis
        val shortUrl =
            cache.get(hash)
                ?: store
                    .findByHashForCacheFill(hash)
                    .orElseThrow { LinkNotFoundException(hash) }
                    .also {
                        // Solo guardamos en cache URLs activas y no caducadas
                        if (
                            it.deactivatedAt == null &&
                            (it.expirationTime == null || Instant.now().isBefore(it.expirationTime))
                        ) {
                            cache.put(it)
                        }
                    }
        // Una URL desactivada ya no debe redirigir al destino
        if (shortUrl.deactivatedAt != null) {
            throw UrlDeactivatedException(hash)
        }
        val expirationTime = shortUrl.expirationTime
        if (expirationTime != null && Instant.now().isAfter(expirationTime)) {
            // url caducada, se elimina de BD. Publico evento para avisar de que lo he borrado
            events.publishEvent(UrlExpirationEvent(hash = hash, expirationTime = expirationTime))
            store.delete(hash)
            // la URL ya no existe: avisa para invalidar la cache
            events.publishEvent(ShortUrlUpdatedEvent(hash = hash))
            throw UrlExpiredException(hash)
        }
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
        return store.deleteExpiredURLPeriodic(now)
    }
}
