package es.unizar.urlshortener.links

import java.time.Instant

// Evento que avisa de que una URL ha sido desactivada.
data class ShortUrlDeactivatedEvent(
    val hash: String,
    val deactivatedAt: Instant,
)
