
package es.unizar.urlshortener.links

import es.unizar.urlshortener.RedisTestContainerConfiguration
import es.unizar.urlshortener.links.adapters.cache.RedisRedirectCache
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.domain.ShortUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
@Import(RedisTestContainerConfiguration::class)
@SpringBootTest
@TestPropertySource(
    properties = ["urlshortener.redirect-cache.provider=redis"],
)
class RedisRedirectCacheTests {
    @Autowired
    lateinit var cache: RedirectCache

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Value("\${urlshortener.redirect-cache.ttl}")
    lateinit var ttl: Duration

    private val hashes = mutableListOf<String>()

    // Eliminamos las entradas creadas para no afectar a otros tests
    @AfterEach
    fun limpiarRedis() {
        hashes.forEach(cache::evict)
    }

    @Test
    fun `dos adaptadores comparten la entrada Redis y su eliminacion`() {
        val hash = "redis-${UUID.randomUUID()}"
        hashes.add(hash)
        val shortUrl = ShortUrl(hash = hash, target = "https://example.com/$hash")

        // Simulamos dos accesos a la misma cache Redis
        val app1Cache = cache
        val app2Cache = RedisRedirectCache(redis, ttl)

        // El primero guarda la URL y el segundo debe poder recuperarla
        app1Cache.put(shortUrl)
        assertEquals(shortUrl.target, app2Cache.get(hash)?.target)

        // Si uno elimina la entrada, el otro tampoco debe encontrarla
        app1Cache.evict(hash)
        assertNull(app2Cache.get(hash))
    }

    @Test
    fun `entrada Redis usa el TTL configurado`() {
        val hash = "redis-ttl-${UUID.randomUUID()}"
        hashes.add(hash)

        // Guardamos una URL sin fecha de caducidad
        cache.put(
            ShortUrl(
                hash = hash,
                target = "https://example.com/$hash",
            ),
        )

        // Consultamos el tiempo que le queda a la entrada en Redis
        val remaining = redis.getExpire("redirect:$hash", TimeUnit.MILLISECONDS)

        // Comprobamos que tiene un TTL positivo y no supera el configurado
        assertTrue(remaining in 1..ttl.toMillis())
    }

    @Test
    fun `URL ya caducada no se almacena en Redis`() {
        val hash = "redis-expired-${UUID.randomUUID()}"
        hashes.add(hash)

        // Intentamos guardar una URL que ya ha caducado
        cache.put(
            ShortUrl(
                hash = hash,
                target = "https://example.com/$hash",
                expirationTime = Instant.now().minusSeconds(1),
            ),
        )

        // Comprobamos que Redis no ha almacenado la URL caducada
        assertNull(redis.opsForValue().get("redirect:$hash"))
    }
}
