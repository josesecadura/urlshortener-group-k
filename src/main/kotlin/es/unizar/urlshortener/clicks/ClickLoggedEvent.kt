package es.unizar.urlshortener.clicks

import java.time.Instant
import java.util.UUID

/**
 * Delivery is at least once: consumers must ignore an [eventId] they have already processed.
 */
data class ClickLoggedEvent(
    val hash: String,
    val occurredAt: Instant = Instant.now(),
    val eventId: UUID = UUID.randomUUID(),
    // Se admiten también los eventos anteriores, que llegaban sin estos dos campos
    val browser: String = "Unknown",
    val platform: String = "Unknown",
)
