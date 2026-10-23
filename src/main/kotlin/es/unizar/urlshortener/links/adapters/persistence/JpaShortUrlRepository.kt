package es.unizar.urlshortener.links.adapters.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface JpaShortUrlRepository : JpaRepository<ShortUrlEntity, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ShortUrlEntity s
           set s.deactivatedAt = :deactivatedAt
         where s.hash = :hash
           and s.managementKeyHash = :managementKeyHash
           and s.deactivatedAt is null
        """,
    )
    fun deactivate(
        @Param("hash") hash: String,
        @Param("managementKeyHash") managementKeyHash: String,
        @Param("deactivatedAt") deactivatedAt: Instant,
    ): Int

    @Modifying
    @Query("DELETE FROM ShortUrlEntity s WHERE s.expirationTime < :now")
    fun deleteExpiredURL(
        @Param("now") now: Instant,
    ): Int // devuelve el numero de filas borradas

    @Query("SELECT s.hash FROM ShortUrlEntity s WHERE s.expirationTime < :now")
    fun getExpiredURL(
        @Param("now") now: Instant,
    ): List<String> // devuelve el numero de filas borradas
}
