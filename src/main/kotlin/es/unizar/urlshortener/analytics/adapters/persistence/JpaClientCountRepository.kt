package es.unizar.urlshortener.analytics.adapters.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// Se leen el total y las parejas en la misma consulta para que correspondan al mismo momento
interface ClientCountRow {
    val totalClicks: Long
    val browser: String?
    val platform: String?
    val clicks: Long?
}

interface JpaClientCountRepository : JpaRepository<ClientCountEntity, ClientCountId> {
    // Se crea el contador con un clic o se suma uno al que ya existe
    @Modifying
    @Query(
        """
        insert into ClientCountEntity (hash, browser, platform, clicks) values (:hash, :browser, :platform, 1)
        on conflict (hash, browser, platform) do update set clicks = clicks + 1
        """,
    )
    fun increment(
        @Param("hash") hash: String,
        @Param("browser") browser: String,
        @Param("platform") platform: String,
    ): Int

    // Se conserva el enlace aunque todavía no tenga ninguna pareja de navegador y plataforma
    @Query(
        """
        select s.totalClicks as totalClicks, c.browser as browser, c.platform as platform, c.clicks as clicks
        from LinkStatsEntity s left join ClientCountEntity c on c.hash = s.hash
        where s.hash = :hash
        """,
    )
    fun countsByHash(
        @Param("hash") hash: String,
    ): List<ClientCountRow>

    @Modifying
    @Query("delete from ClientCountEntity c where c.hash = :hash")
    fun deleteByHash(
        @Param("hash") hash: String,
    ): Int
}
