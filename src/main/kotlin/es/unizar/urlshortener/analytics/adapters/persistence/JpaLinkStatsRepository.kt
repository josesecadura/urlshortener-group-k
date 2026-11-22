package es.unizar.urlshortener.analytics.adapters.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface JpaLinkStatsRepository : JpaRepository<LinkStatsEntity, String> {
    // Se rellena la fecha una sola vez, aunque se vuelva a recibir el aviso
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LinkStatsEntity s set s.deactivatedAt = :date where s.hash = :hash and s.deactivatedAt is null")
    fun markDeactivated(
        @Param("hash") hash: String,
        @Param("date") date: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        insert into LinkStatsEntity (hash, totalClicks) values (:hash, 1)
        on conflict (hash) do update set totalClicks = totalClicks + 1
        """,
    )
    fun upsertIncrement(
        @Param("hash") hash: String,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        insert into LinkStatsEntity (hash, totalClicks) values (:hash, 0)
        on conflict (hash) do update set totalClicks = totalClicks
        """,
    )
    fun insertIfAbsent(
        @Param("hash") hash: String,
    ): Int
}
