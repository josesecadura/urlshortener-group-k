package es.unizar.urlshortener.analytics.application

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import org.jmolecules.architecture.hexagonal.PrimaryPort
import org.jmolecules.architecture.hexagonal.SecondaryPort
import java.time.Duration
import java.time.Instant

/** Per-hash click totals (one row per short URL). Not the permanent click log. */
data class LinkStats(
    val hash: String,
    val totalClicks: Long,
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

    fun insertIfAbsent(hash: String)

    fun incrementClicks(event: ClickLoggedEvent)

    fun deleteProcessedClicksBefore(cutoff: Instant): Int
}

@PrimaryPort
interface UpdateLinkStats {
    fun onCreated(event: ShortUrlCreatedEvent)

    fun onClick(event: ClickLoggedEvent)
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
