package es.unizar.urlshortener.links

import es.unizar.urlshortener.links.application.CreateShortUrl
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.application.RedirectShortUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

@SpringBootTest
class RedirectCacheTests {
    @Autowired
    lateinit var createShortUrl: CreateShortUrl

    @Autowired
    lateinit var redirectShortUrl: RedirectShortUrl

    @Autowired
    lateinit var cache: RedirectCache

    @Autowired
    lateinit var events: ApplicationEventPublisher

    @Test
    fun `redirect fills the cache and ShortUrlUpdatedEvent invalidates it`() {
        val target = "https://example.com/${UUID.randomUUID()}"
        val hash = createShortUrl.create(target).hash
        assertNull(cache.get(hash)) // aún no se ha redirigido: caché vacía

        // primer redirect: va a BD y deja la entrada en caché
        assertEquals(target, redirectShortUrl.redirect(hash).target)
        assertNotNull(cache.get(hash))

        // se publica el evento nuevo: el consumidor debe sacar la entrada de la caché
        events.publishEvent(ShortUrlUpdatedEvent(hash))
        assertNull(cache.get(hash))
    }
}
