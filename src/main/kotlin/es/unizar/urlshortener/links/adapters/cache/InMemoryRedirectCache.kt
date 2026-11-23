package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.domain.ShortUrl
import java.util.concurrent.ConcurrentHashMap

// Cache en memoria local ahora mismo luego se hará con redis
class InMemoryRedirectCache : RedirectCache {
    // Almacenamos las URLs con ese hash
    private val entries = ConcurrentHashMap<String, ShortUrl>()

    // Buscamos la url por el hash en cache, si no está devolvemos null
    override fun get(hash: String): ShortUrl? = entries[hash]

    // Guardamos url en caché
    override fun put(shortUrl: ShortUrl) {
        entries[shortUrl.hash] = shortUrl
    }

    // Eliminamos de cache la url
    override fun evict(hash: String) {
        entries.remove(hash)
    }
}
