package es.unizar.urlshortener.links.application

import es.unizar.urlshortener.links.domain.ShortUrl
import org.jmolecules.architecture.hexagonal.PrimaryPort
import org.jmolecules.architecture.hexagonal.SecondaryPort
import java.time.Instant
import java.util.Optional

@SecondaryPort
interface ShortUrlStore {
    fun insert(shortUrl: ShortUrl): ShortUrl

    fun findByHash(hash: String): Optional<ShortUrl>

    fun delete(hash: String)

    fun deleteExpiredURLPeriodic(now: Instant): Int

    fun getExpiredURLPeriodic(now: Instant): List<String>
}

@SecondaryPort
interface RedirectCache {
    // Busca url corta en cache usando el hash
    fun get(hash: String): ShortUrl?

    // Guarda url corta en cache usando el hash
    fun put(shortUrl: ShortUrl)

    // Elimina url corta de cache usando el hash
    fun evict(hash: String)
}

@SecondaryPort
fun interface UrlValidator {
    fun isValid(url: String): Boolean
}

@SecondaryPort
fun interface ShortCodeSource {
    fun next(): String
}

data class CreatedShortUrl(
    val hash: String,
    val target: String,
)

data class Redirection(
    val target: String,
)

class InvalidUrlException(
    url: String,
) : RuntimeException("[$url] is not a supported URL")

class LinkNotFoundException(
    hash: String,
) : RuntimeException("[$hash] is not known")

class UrlExpiredException(
    hash: String,
) : RuntimeException("[$hash] has expired")

@PrimaryPort
fun interface CreateShortUrl {
    fun create(
        url: String,
        expirationTime: Instant?,
    ): CreatedShortUrl

    // Para crear URL sin fecha de caducidad, al create de arriba no puedo poner expirationTime: Instant?=Null
    // para valor por defecto al ser un metodo abstracto
    fun create(url: String) = create(url, null)
}

@PrimaryPort
fun interface RedirectShortUrl {
    fun redirect(hash: String): Redirection
}

@PrimaryPort
fun interface ExpiredURL {
    fun deleteExpiredURL(now: Instant): Int
}
