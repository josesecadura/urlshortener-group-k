package es.unizar.urlshortener.links.application

import es.unizar.urlshortener.links.domain.ShortUrl
import org.jmolecules.architecture.hexagonal.PrimaryPort
import org.jmolecules.architecture.hexagonal.SecondaryPort
import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletableFuture

@SecondaryPort
interface ShortUrlStore {
    fun insert(shortUrl: ShortUrl): ShortUrl

    fun findByHash(hash: String): Optional<ShortUrl>

    // Buscar URL bloqueando su fila para poder guardarla en cache
    fun findByHashForCacheFill(hash: String): Optional<ShortUrl>

    // Devuelve 1 si se desactiva la URL y 0 si ya estaba desactivada.
    fun deactivate(
        hash: String,
        managementKeyHash: String,
        deactivatedAt: Instant,
    ): Int

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

@PrimaryPort
fun interface InvalidateRedirectCache {
    fun invalidate(hash: String)
}

@SecondaryPort
fun interface UrlValidator {
    fun isValid(url: String): Boolean
}

@SecondaryPort
fun interface ShortCodeSource {
    fun next(): String
}

@SecondaryPort
fun interface QrCodeEncoder {
    fun encode(content: String): ByteArray
}

data class CreatedShortUrl(
    val hash: String,
    val target: String,
)

/** The plaintext management key is returned only at creation time. */
data class ManagedShortUrlCreated(
    val hash: String,
    val target: String,
    val managementKey: String,
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

class InvalidManagementKeyException : RuntimeException("Invalid management key")

class UrlDeactivatedException(
    hash: String,
) : RuntimeException("[$hash] has been deactivated")

@PrimaryPort
fun interface DeactivateManagedShortUrl {
    fun deactivate(
        hash: String,
        managementKey: String,
    )
}

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
fun interface CreateManagedShortUrl {
    fun createManaged(url: String): ManagedShortUrlCreated
}

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
fun interface ResolveShortUrl {
    fun resolve(hash: String): Redirection
}

@PrimaryPort
fun interface GenerateQrCode {
    fun generate(shortUrl: String): CompletableFuture<ByteArray>
}

@PrimaryPort
fun interface ExpiredURL {
    fun deleteExpiredURL(now: Instant): Int
}
