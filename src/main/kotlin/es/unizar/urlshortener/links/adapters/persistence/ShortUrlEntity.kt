package es.unizar.urlshortener.links.adapters.persistence

import es.unizar.urlshortener.links.domain.ShortUrl
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "short_url")
class ShortUrlEntity(
    @Id
    var hash: String,
    var target: String,
    var createdAt: Instant = Instant.now(),
    var expirationTime: Instant? =null, //almacena el instante de caducidad si existe, si no caduca -> null

)

fun ShortUrl.toEntity() =
    ShortUrlEntity(
        hash = hash,
        target = target,
        createdAt = createdAt,
        expirationTime = expirationTime,
    )

fun ShortUrlEntity.toDomain() =
    ShortUrl(
        hash = hash,
        target = target,
        createdAt = createdAt,
        expirationTime = expirationTime,
    )
