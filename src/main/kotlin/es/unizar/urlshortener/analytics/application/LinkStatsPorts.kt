package es.unizar.urlshortener.analytics.application

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.links.ShortUrlDeactivatedEvent
import es.unizar.urlshortener.links.UrlExpirationEvent
import org.jmolecules.architecture.hexagonal.PrimaryPort
import org.jmolecules.architecture.hexagonal.SecondaryPort
import java.time.Duration
import java.time.Instant

/** Per-hash click totals (one row per short URL). Not the permanent click log. */
data class LinkStats(
    val hash: String,
    val totalClicks: Long,
    // Una fecha vacía indica que aún no se ha recibido una desactivación
    val deactivatedAt: Instant? = null,
)

// Se devuelve un contador por cada familia que aparece en el enlace
data class ClientStats(
    val browsers: Map<String, Long>,
    val platforms: Map<String, Long>,
)

@PrimaryPort
fun interface GetClientStats {
    fun clientsByHash(hash: String): ClientStats?
}

@SecondaryPort
interface LinkStatsStore {
    fun clientsByHash(hash: String): ClientStats?

    fun findByHash(hash: String): LinkStats?

    fun markDeactivated(
        hash: String,
        deactivatedAt: Instant,
    )

    fun insertIfAbsent(hash: String)

    fun incrementClicks(event: ClickLoggedEvent)

    fun deleteProcessedClicksBefore(cutoff: Instant): Int

    fun deleteByID(hash: String)
}

@PrimaryPort
interface UpdateLinkStats {
    fun onCreated(event: ShortUrlCreatedEvent)

    fun onDeactivated(event: ShortUrlDeactivatedEvent)

    fun onClick(event: ClickLoggedEvent)

    fun onDelete(event: UrlExpirationEvent)
}

@PrimaryPort
fun interface PruneProcessedClickEvents {
    /** Forgets processed click ids older than [retention]; returns how many were removed. */
    fun pruneOlderThan(retention: Duration): Int
}

@PrimaryPort
fun interface GetLinkStats {
    fun findByHash(hash: String): LinkStats?
}
