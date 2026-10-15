package es.unizar.urlshortener.links.scheduling.persistence
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "shedlock")
class ShedlockEntity(
    @Id
    var name: String,
    var lockUntil: Instant,
    var lockedAt: Instant,
    var lockedBy: String,
)
