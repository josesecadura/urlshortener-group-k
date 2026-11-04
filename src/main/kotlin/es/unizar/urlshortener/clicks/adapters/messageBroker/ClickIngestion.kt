package es.unizar.urlshortener.clicks.adapters.messageBroker

import es.unizar.urlshortener.clicks.ClickLoggedIngestionEvent
import es.unizar.urlshortener.clicks.application.RecordClickIngestion
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Profile("broker")
@Component
class ClickIngestion(
    private val recordClickIngestion: RecordClickIngestion,
) {
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ClickLoggedIngestionEvent) {
        recordClickIngestion.recordIngestion(event)
    }
}
