package es.unizar.urlshortener.analytics.adapters.persistence

import es.unizar.urlshortener.analytics.application.LinkStats
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "link_stats")
class LinkStatsEntity(
    @Id
    var hash: String,
    var totalClicks: Long = 0,
    var expirationTime: Instant? =null, //almacena el instante de caducidad si existe, si no caduca -> null
)

fun LinkStatsEntity.toDomain() = LinkStats(hash = hash, totalClicks = totalClicks)
