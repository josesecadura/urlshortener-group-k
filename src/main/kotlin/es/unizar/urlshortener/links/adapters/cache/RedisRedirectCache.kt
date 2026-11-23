
package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.domain.ShortUrl
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class RedisRedirectCache(
    private val redis: StringRedisTemplate,
) : RedirectCache {
    // Tiempo maximo que una URL permanece en cache
    private val ttl = Duration.ofMinutes(5)

    // Prefijo para identificar nuestras claves en Redis
    private fun key(hash: String) = "redirect:$hash"

    // Buscar una URL en Redis
    override fun get(hash: String): ShortUrl? {
        val value = redis.opsForValue().get(key(hash)) ?: return null

        // Formato almacenado: expiracion|target
        val separator = value.indexOf('|')
        if (separator < 0) return null

        val expiration = value.substring(0, separator)
        val target = value.substring(separator + 1)

        val expirationTime =
            expiration.takeIf { it.isNotEmpty() }
                ?.toLong()
                ?.let { Instant.ofEpochMilli(it) }

        return ShortUrl(
            hash = hash,
            target = target,
            expirationTime = expirationTime,
        )
    }

    // Guardar una URL con TTL
    override fun put(shortUrl: ShortUrl) {
        val remaining =
            shortUrl.expirationTime?.let {
                Duration.between(Instant.now(), it)
            }

        val duration =
            if (remaining != null && remaining < ttl) remaining else ttl

        // No almacenar URLs que ya han caducado
        if (duration.isZero || duration.isNegative) {
            evict(shortUrl.hash)
            return
        }

        val expiration = shortUrl.expirationTime?.toEpochMilli() ?: ""
        val value = "$expiration|${shortUrl.target}"

        redis.opsForValue().set(key(shortUrl.hash), value, duration)
    }

    // Eliminar una URL de Redis
    override fun evict(hash: String) {
        redis.delete(key(hash))
    }
}
