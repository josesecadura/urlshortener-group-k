
package es.unizar.urlshortener.links

import es.unizar.urlshortener.links.application.CreateShortUrl
import es.unizar.urlshortener.links.application.LinkNotFoundException
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.application.RedirectShortUrl
import es.unizar.urlshortener.links.application.ShortUrlStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
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

    // Spy para contar las consultas reales al repositorio
    @MockitoSpyBean
    lateinit var store: ShortUrlStore

    private val hashesCreados = mutableListOf<String>()

    // Crear una URL y registrar su hash para limpiarla despues
    private fun crearUrl(): Pair<String, String> {
        val target = "https://example.com/${UUID.randomUUID()}"
        val hash = createShortUrl.create(target).hash
        hashesCreados.add(hash)
        return hash to target
    }

    // Limpiar los datos creados despues de cada test
    @AfterEach
    fun limpiar() {
        hashesCreados.forEach { hash ->
            try {
                cache.evict(hash)
            } finally {
                store.delete(hash)
            }
        }
        hashesCreados.clear()
    }

    @Test
    fun `segunda redireccion utiliza cache sin consultar otra vez la BD`() {
        val (hash, target) = crearUrl()

        // Inicialmente no esta en cache
        assertNull(cache.get(hash))

        // Primera redireccion: consulta BD y guarda en cache
        assertEquals(target, redirectShortUrl.redirect(hash).target)
        assertNotNull(cache.get(hash))

        // Segunda redireccion: reutiliza la cache
        assertEquals(target, redirectShortUrl.redirect(hash).target)

        // El repositorio solo se ha consultado una vez
        verify(store, times(1)).findByHash(hash)
    }

    @Test
    fun `ShortUrlUpdatedEvent invalida la cache`() {
        val (hash, target) = crearUrl()

        // Introducir URL en cache
        assertEquals(target, redirectShortUrl.redirect(hash).target)
        assertNotNull(cache.get(hash))

        // Publicar evento de actualizacion
        events.publishEvent(ShortUrlUpdatedEvent(hash))

        // Comprobar que se ha eliminado de cache
        assertNull(cache.get(hash))
    }

    @Test
    fun `hash inexistente no se almacena en cache`() {
        val hash = "inexistente-${UUID.randomUUID()}"

        // Una URL inexistente debe provocar una excepcion
        assertThrows(LinkNotFoundException::class.java) {
            redirectShortUrl.redirect(hash)
        }

        // No debe guardarse en cache
        assertNull(cache.get(hash))
    }
}
