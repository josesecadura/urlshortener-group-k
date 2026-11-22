package es.unizar.urlshortener.analytics.adapters.persistence

import es.unizar.urlshortener.analytics.application.ClientStats
import es.unizar.urlshortener.analytics.application.LinkStats
import es.unizar.urlshortener.analytics.application.LinkStatsStore
import es.unizar.urlshortener.clicks.ClickLoggedEvent
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JpaLinkStatsStore(
    private val repository: JpaLinkStatsRepository,
    private val processed: JpaProcessedClickEventRepository,
    private val clients: JpaClientCountRepository,
) : LinkStatsStore {
    override fun findByHash(hash: String): LinkStats? = repository.findById(hash).map { it.toDomain() }.orElse(null)

    override fun insertIfAbsent(hash: String) {
        repository.insertIfAbsent(hash)
    }

    override fun incrementClicks(event: ClickLoggedEvent) {
        // Si ya se ha procesado el evento se dejan todos los contadores como estaban
        if (processed.existsById(event.eventId)) return
        processed.insert(event.eventId, Instant.now())

        // Se suma un clic al enlace y otro a la pareja de navegador y plataforma
        repository.upsertIncrement(event.hash)
        clients.increment(event.hash, event.browser, event.platform)
    }

    override fun clientsByHash(hash: String): ClientStats? {
        val rows = clients.countsByHash(hash)
        // La consulta parte del enlace, así que solo se recibe una lista vacía si no existe
        if (rows.isEmpty()) return null
        val browsers = mutableMapOf<String, Long>()
        val platforms = mutableMapOf<String, Long>()
        var olderClicks = rows.first().totalClicks
        for (row in rows) {
            // En un enlace sin parejas todavía, el cruce devuelve una fila sin contador
            val count = row.clicks ?: continue
            val browser = requireNotNull(row.browser)
            val platform = requireNotNull(row.platform)
            browsers[browser] = browsers.getOrDefault(browser, 0L) + count
            platforms[platform] = platforms.getOrDefault(platform, 0L) + count
            olderClicks -= count
        }
        // De los clics anteriores a la detección solo se conoce el total
        if (olderClicks > 0) {
            browsers["Unknown"] = browsers.getOrDefault("Unknown", 0L) + olderClicks
            platforms["Unknown"] = platforms.getOrDefault("Unknown", 0L) + olderClicks
        }
        return ClientStats(browsers, platforms)
    }

    override fun deleteProcessedClicksBefore(cutoff: Instant): Int = processed.deleteProcessedBefore(cutoff)
}
