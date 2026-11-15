package es.unizar.urlshortener.links.adapters.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface JpaShortUrlRepository : JpaRepository<ShortUrlEntity, String> {
    @Modifying
    @Query("DELETE FROM ShortUrlEntity s WHERE s.expirationTime < :now")
    fun deleteExpiredURL(
        @Param("now") now: Instant,
    ): Int // elimina de la BD las url cuya fecha de expiracion es anterior a la fecha actual (han expirado)

    @Query("SELECT s.hash FROM ShortUrlEntity s WHERE s.expirationTime < :now")
    fun getExpiredURL(
        @Param("now") now: Instant,
    ): List<String> // devuelve el numero de filas borradas
}
