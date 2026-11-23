package es.unizar.urlshortener.clicks.application

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.clicks.ClickLoggedIngestionEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecordClickService(
    private val store: ClickStore,
) : RecordClick,
    RecordClickIngestion {
    @Transactional
    override fun record(event: ClickLoggedEvent) {
        if (store.existsByEventId(event.eventId)) return
        store.save(event.toClick())
    }

    @Transactional
    override fun recordIngestion(event: ClickLoggedIngestionEvent) {
        if (store.existsByEventId(event.eventId)) return
        store.save(event.toClick())
    }
}

fun ClickLoggedEvent.toClick() = Click(hash = hash, occurredAt = occurredAt, eventId = eventId)

fun ClickLoggedIngestionEvent.toClick() = Click(hash = hash, occurredAt = occurredAt, eventId = eventId)
