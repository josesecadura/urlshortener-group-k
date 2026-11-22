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

// Se pasan solo las dos familias que se necesitan para contar los clics
data class ClientInfo(
    val browser: String = "Unknown",
    val platform: String = "Unknown",
)

@PrimaryPort
fun interface RedirectShortUrl {
    fun redirect(
        hash: String,
        client: ClientInfo,
    ): Redirection

    // Se permite redirigir también desde llamadas que no tienen cabeceras HTTP
    fun redirect(hash: String): Redirection = redirect(hash, ClientInfo())
}

@PrimaryPort
fun interface ExpiredURL {
    fun deleteExpiredURL(now: Instant): Int
}
