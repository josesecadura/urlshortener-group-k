package es.unizar.urlshortener.links.scheduling
import es.unizar.urlshortener.links.application.ExpiredURL
import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ScheduledTasks(
    private val expiredURL: ExpiredURL,
) {
    // nombre del bloqueante, como todas las replicas ejecutan este mismo codigo van a la tabla de shedlock y buscan la misma fila (id=name)
    // bloqueo durante 1 minuto, no se puede ejecutar el metodo en el minuto siguiente a la ejecucion anterior.
    // En caso de que el nodo se bloquee/muera, maximo tiempo espera 5 minutos
    @Scheduled(fixedRate = 60000 * 5) // se ejecuta cada 60s=1min * 5 =5 min
    @SchedulerLock(name = "deleteExpiredURL", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    fun periodicDeleteExpiratedURL() {
        LockAssert.assertLocked()
        expiredURL.deleteExpiredURL(Instant.now())
    }
}
