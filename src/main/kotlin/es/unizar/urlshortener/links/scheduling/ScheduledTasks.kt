package es.unizar.urlshortener.links.scheduling
import es.unizar.urlshortener.links.application.ExpiredURL
import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Component
class ScheduledTasks(
    private val expiredURL: ExpiredURL,
    private val incomplete: IncompleteEventPublications,
) {
    // nombre del bloqueante, como todas las replicas ejecutan este mismo codigo van a la tabla de shedlock y buscan la misma fila (id=name)
    // bloqueo durante 1 minuto, no se puede ejecutar el metodo en el minuto siguiente a la ejecucion anterior.
    // En caso de que el nodo se bloquee/muera, maximo tiempo espera 5 minutos
    @Scheduled(fixedRate = 60000 * 5) // se ejecuta cada 60s=1min * 5 =5 min
    @SchedulerLock(name = "deleteExpiredURL", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    @Transactional
    fun periodicDeleteExpiratedURL() {
        LockAssert.assertLocked()
        expiredURL.deleteExpiredURL(Instant.now())
    }

    // reenvia los eventos que no han podido ser tratados o que al tratarlos ha saltado excepcion (ej. broker rabbitmq caido),
    // le vuelve a llegar a la funcion y los vuelve a tratar
    @Scheduled(fixedRateString = "\${urlshortener.events.retry-interval:PT5M}")
    @SchedulerLock(name = "incompleteEventsSend", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    fun incompleteEventsSend() {
        LockAssert.assertLocked()
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofSeconds(30))
    }
}
