package es.unizar.urlshortener.analytics

import es.unizar.urlshortener.analytics.application.GetClientStats
import es.unizar.urlshortener.analytics.application.GetLinkStats
import es.unizar.urlshortener.analytics.application.UpdateLinkStats
import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class LinkStatsConcurrencyTests {
    @Autowired
    lateinit var updateLinkStats: UpdateLinkStats

    @Autowired
    lateinit var getLinkStats: GetLinkStats

    @Autowired
    lateinit var mapper: ObjectMapper

    @Autowired
    lateinit var getClientStats: GetClientStats

    @Test
    fun `concurrent first clicks on a missing row are all counted`() {
        val hash = "race-${UUID.randomUUID()}"
        runConcurrently(CLICKS) { updateLinkStats.onClick(ClickLoggedEvent(hash = hash)) }
            .forEach { it.getOrThrow() }
        assertEquals(CLICKS.toLong(), getLinkStats.findByHash(hash)?.totalClicks)
        assertEquals(mapOf("Unknown" to CLICKS.toLong()), getClientStats.clientsByHash(hash)?.browsers)
    }

    @Test
    fun `created event racing with clicks does not reset the counter`() {
        val hash = "race-${UUID.randomUUID()}"
        runConcurrently(CLICKS + 1) { index ->
            if (index == 0) {
                updateLinkStats.onCreated(ShortUrlCreatedEvent(hash = hash, target = "https://example.com"))
            } else {
                updateLinkStats.onClick(ClickLoggedEvent(hash = hash))
            }
        }.forEach { it.getOrThrow() }
        assertEquals(CLICKS.toLong(), getLinkStats.findByHash(hash)?.totalClicks)
    }

    @Test
    fun `se puede procesar un evento guardado antes de añadir las familias`() {
        val hash = "old-${UUID.randomUUID()}"
        // Se recupera un evento con los tres campos que se guardaban antes
        val json = """{"hash":"$hash","occurredAt":"2026-10-01T12:00:00Z","eventId":"${UUID.randomUUID()}"}"""
        val event = mapper.readValue(json, ClickLoggedEvent::class.java)
        updateLinkStats.onClick(event)

        // Se conserva el clic aunque no se sepa desde dónde se hizo
        val clients = getClientStats.clientsByHash(hash)
        assertEquals(mapOf("Unknown" to 1L), clients?.browsers)
        assertEquals(mapOf("Unknown" to 1L), clients?.platforms)
    }

    @Test
    fun `a redelivered click is counted once`() {
        val event = ClickLoggedEvent(hash = "redelivery-${UUID.randomUUID()}", browser = "Chrome", platform = "Windows")
        repeat(REDELIVERIES) { updateLinkStats.onClick(event) }
        assertEquals(1L, getLinkStats.findByHash(event.hash)?.totalClicks)
        // Se comprueba que repetir el evento tampoco suma otra vez en los dos mapas
        val clients = getClientStats.clientsByHash(event.hash)
        assertEquals(mapOf("Chrome" to 1L), clients?.browsers)
        assertEquals(mapOf("Windows" to 1L), clients?.platforms)
    }

    /** Copies racing past the processed-id check fail on its primary key and roll back; one is counted. */
    @Test
    fun `concurrent copies of one click are counted once`() {
        val event = ClickLoggedEvent(hash = "redelivery-${UUID.randomUUID()}")
        val results = runConcurrently(CLICKS) { updateLinkStats.onClick(event) }
        assert(results.any { it.isSuccess }) { "no copy succeeded: ${results.mapNotNull { it.exceptionOrNull() }}" }
        assertEquals(1L, getLinkStats.findByHash(event.hash)?.totalClicks)
        // Se comprueba que repetir el evento tampoco suma otra vez en los dos mapas
        val clients = getClientStats.clientsByHash(event.hash)
        assertEquals(mapOf("Unknown" to 1L), clients?.browsers)
        assertEquals(mapOf("Unknown" to 1L), clients?.platforms)
    }

    private fun runConcurrently(
        tasks: Int,
        action: (Int) -> Unit,
    ): List<Result<Unit>> {
        val pool = Executors.newFixedThreadPool(tasks)
        val start = CountDownLatch(1)
        val futures =
            (0 until tasks).map { index ->
                pool.submit {
                    start.await()
                    action(index)
                }
            }
        start.countDown()
        return futures
            .map { future ->
                runCatching { future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                    .map { }
                    .recoverCatching { throw (it as? ExecutionException)?.cause ?: it }
            }.also { pool.shutdown() }
    }

    private companion object {
        const val CLICKS = 16
        const val REDELIVERIES = 3
        const val TIMEOUT_SECONDS = 20L
    }
}
