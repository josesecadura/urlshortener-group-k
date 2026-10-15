package es.unizar.urlshortener.links

import java.time.Instant

data class UrlExpirationEvent(
    val hash: String,
    var expirationTime: Instant? = null,
)
