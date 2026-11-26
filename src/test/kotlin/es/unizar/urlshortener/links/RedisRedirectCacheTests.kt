package es.unizar.urlshortener.links

import com.jayway.jsonpath.JsonPath.read
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
@Import(RedisTestContainerConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
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
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var createManaged: CreateManagedShortUrl

    @Autowired
    lateinit var deactivateManaged: DeactivateManagedShortUrl

    @Autowired
    lateinit var redirect: RedirectShortUrl

    @Autowired
    lateinit var store: ShortUrlStore

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
    fun `redirigir una URL guarda su destino en Redis con TTL`() {
        val target = "https://example.com/${UUID.randomUUID()}"

        // Creamos una URL desde el endpoint
        val created =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", target),
                ).andExpect(status().isCreated)
                .andReturn()

        val hash = read<String>(created.response.contentAsString, "$.hash")
        hashes.add(hash)
        storedHashes.add(hash)

        // Antes de redirigir la URL no debe estar en cache
        assertNull(cache.get(hash))

        // La primera redireccion devuelve el destino y lo guarda en Redis
        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", target))

        // Comprobamos que Redis guarda el destino con el TTL configurado
        assertEquals(target, cache.get(hash)?.target)
        val remaining = redis.getExpire("redirect:$hash", TimeUnit.MILLISECONDS)
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

    @Test
    fun `clave de gestion incorrecta no invalida Redis`() {
        val target = "https://example.com/${UUID.randomUUID()}"
        val created = createManaged.createManaged(target)
        val hash = created.hash

        hashes.add(hash)
        storedHashes.add(hash)

        // Redirigimos una vez para guardar la URL en Redis
        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)

        val redisKey = "redirect:$hash"
        val entryBefore = redis.opsForValue().get(redisKey)
        assertNotNull(entryBefore)

        // Intentamos desactivar la URL con una clave incorrecta
        mockMvc
            .perform(
                delete("/api/managed-links/$hash")
                    .header("Authorization", "Bearer clave-incorrecta"),
            ).andExpect(status().isForbidden)

        // Comprobamos que la entrada de Redis no se ha eliminado
        assertEquals(entryBefore, redis.opsForValue().get(redisKey))

        // La URL sigue activa y redirige correctamente
        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", target))
    }

    @Test
    fun `hash inexistente devuelve 404 y no se guarda en Redis`() {
        val hash = "inexistente-${UUID.randomUUID()}"
        hashes.add(hash)

        // Intentamos redirigir una URL que no existe
        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isNotFound)
            .andExpect(header().doesNotExist("Location"))
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))

        // Comprobamos que no se ha creado ninguna entrada en Redis
        assertNull(redis.opsForValue().get("redirect:$hash"))
    }
}
