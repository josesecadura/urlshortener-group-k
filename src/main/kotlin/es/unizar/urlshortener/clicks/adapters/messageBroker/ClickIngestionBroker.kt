package es.unizar.urlshortener.clicks.adapters.messageBroker

import es.unizar.urlshortener.clicks.ClickLoggedIngestionEvent
import es.unizar.urlshortener.clicks.application.RecordClickIngestion
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Profile("broker")
@Component
class ClickIngestionBroker(
    private val recordClickIngestion: RecordClickIngestion,
    private var eventsRabbit: RabbitTemplate,
) {
    // se crea un listener de rabbitmq, para que al publicar un mensaje en la cola se lea aqui y ejecute esta accion (si no se usa broker esto se hace con un listener de eventos)
    @RabbitListener(queues = ["clicks.ingest.queue"])
    fun on(event: ClickLoggedIngestionEvent) {
        recordClickIngestion.recordIngestion(event)
    }
}
