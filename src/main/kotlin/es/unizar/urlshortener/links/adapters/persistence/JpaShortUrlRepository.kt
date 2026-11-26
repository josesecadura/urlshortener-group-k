package es.unizar.urlshortener.links.adapters.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface JpaShortUrlRepository : JpaRepository<ShortUrlEntity, String> {
    // Bloqueamos la fila pq otra replica podria desactivar la URL mientras la guardamos en Redis
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShortUrlEntity s where s.hash = :hash")
    fun findByHashForCacheFill(
        @Param("hash") hash: String,
    ): Optional<ShortUrlEntity>

    // Esta consulta evita que dos peticiones desactiven la misma URL
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
