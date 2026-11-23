
package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.domain.ShortUrl
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
@ConditionalOnProperty(
    prefix = "urlshortener.redirect-cache",
    name = ["provider"],
    havingValue = "redis",
)
class RedisRedirectCache(
    private val redis: StringRedisTemplate,
    @Value("\${urlshortener.redirect-cache.ttl}")
    private val ttl: Duration,
) : RedirectCache {
    // Prefijo como id para las claves en Redis
    private fun key(hash: String) = "redirect:$hash"

    private fun parseCachedUrl(
        hash: String,
        value: String,
    ): ShortUrl? =
        value
            .indexOf('|')
            .takeIf { it >= 0 }
            ?.let { separator ->
                val expirationText = value.substring(0, separator)
                val expirationTime =
                    if (expirationText.isEmpty()) {
                        null
                    } else {
                        expirationText.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
                    }

                if (expirationText.isNotEmpty() && expirationTime == null) {
                    null
                } else {
                    ShortUrl(
                        hash = hash,
                        target = value.substring(separator + 1),
                        expirationTime = expirationTime,
                    )
                }
            }

    // Buscar una URL en Redis
    override fun get(hash: String): ShortUrl? =
        redis
            .opsForValue()
            .get(key(hash))
            ?.let { value ->
                parseCachedUrl(hash, value).also { cachedUrl ->
                    if (cachedUrl == null) {
                        evict(hash)
                    }
                }
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
