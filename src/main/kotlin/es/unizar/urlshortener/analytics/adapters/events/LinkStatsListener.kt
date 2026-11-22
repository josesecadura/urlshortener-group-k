package es.unizar.urlshortener.analytics.adapters.events

import es.unizar.urlshortener.analytics.application.UpdateLinkStats
import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class LinkStatsListener(
    private val updateLinkStats: UpdateLinkStats,
) {
    // Se prepara la fila antes de devolver el enlace para que ya se pueda consultar con cero clics
    @EventListener
    fun onCreated(event: ShortUrlCreatedEvent) {
        updateLinkStats.onCreated(event)
    }

    @ApplicationModuleListener
    fun onClick(event: ClickLoggedEvent) {
        updateLinkStats.onClick(event)
    }
}
