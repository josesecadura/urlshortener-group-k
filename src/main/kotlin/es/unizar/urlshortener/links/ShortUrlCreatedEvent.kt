package es.unizar.urlshortener.links

import java.time.Instant

/**
 * Published after a short URL is persisted.
 */
data class ShortUrlCreatedEvent(
    val hash: String,
    val target: String,
    val createdAt: Instant = Instant.now(),
)

data class UrlExpiration(
    val hash: String,
    var expirationTime: Instant? =null,
)
