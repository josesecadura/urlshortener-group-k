package es.unizar.urlshortener.links

import java.time.Instant

/**
 * Cuando una URL se elimina o caduca, se publica este evento.
 * Las copias almacenadas en caché deben descartarse.
 */
data class ShortUrlUpdatedEvent(
    val hash: String,
    val updatedAt: Instant = Instant.now(),
)
