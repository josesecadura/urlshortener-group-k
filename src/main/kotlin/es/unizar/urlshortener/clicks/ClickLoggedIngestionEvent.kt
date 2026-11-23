package es.unizar.urlshortener.clicks

import java.time.Instant
import java.util.UUID

data class ClickLoggedIngestionEvent(
    val hash: String,
    val occurredAt: Instant = Instant.now(),
    val eventId: UUID = UUID.randomUUID(),
)
