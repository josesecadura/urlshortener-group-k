
package es.unizar.urlshortener.links

import es.unizar.urlshortener.RedisTestContainerConfiguration
import es.unizar.urlshortener.links.adapters.cache.RedisRedirectCache
import es.unizar.urlshortener.links.application.CreateManagedShortUrl
import es.unizar.urlshortener.links.application.DeactivateManagedShortUrl
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.application.RedirectShortUrl
import es.unizar.urlshortener.links.application.ShortUrlStore
import es.unizar.urlshortener.links.application.UrlDeactivatedException
import es.unizar.urlshortener.links.domain.ShortUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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

    @Autowired
    lateinit var createManaged: CreateManagedShortUrl

    @Autowired
    lateinit var deactivateManaged: DeactivateManagedShortUrl

    @Autowired
    lateinit var redirect: RedirectShortUrl

    @Autowired
    lateinit var store: ShortUrlStore

    // Guardamos los hashes para limpiar los datos al terminar cada test
    // Entradas de redis a eliminar
    private val hashes = mutableListOf<String>()

    // Entradas de PostgreSQL a eliminar
    private val storedHashes = mutableListOf<String>()

    @AfterEach
    fun limpiarRedis() {
        // Eliminamos las entradas creadas en Redis
        hashes.forEach(cache::evict)

        // Eliminamos las URLs creadas en PostgreSQL
        storedHashes.forEach(store::delete)

        hashes.clear()
        storedHashes.clear()
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

    @Test
    fun `desactivar URL invalida Redis e impide nuevas redirecciones`() {
        val target = "https://example.com/${UUID.randomUUID()}"

        // Creamos una URL gestionable con su clave de administracion
        val created = createManaged.createManaged(target)
        val hash = created.hash

        // Registramos el hash para limpiar Redis y PostgreSQL al terminar
        hashes.add(hash)
        storedHashes.add(hash)

        // Primera redireccion: funciona y guarda la URL en Redis
        assertEquals(target, redirect.redirect(hash).target)
        assertNotNull(cache.get(hash))

        // Desactivamos la URL utilizando su clave de gestion
        deactivateManaged.deactivate(hash, created.managementKey)

        // Comprobamos que el evento ha eliminado la entrada de Redis
        assertNull(cache.get(hash))

        // La URL desactivada ya no debe permitir redirecciones
        assertThrows(UrlDeactivatedException::class.java) {
            redirect.redirect(hash)
        }

        // Comprobamos que tampoco vuelve a funcionar en la segunda peticion
        assertThrows(UrlDeactivatedException::class.java) {
            redirect.redirect(hash)
        }

        // La URL desactivada no debe volver a guardarse en Redis
        assertNull(cache.get(hash))
    }
}
