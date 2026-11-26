package es.unizar.urlshortener.links.application

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class QrCodeService(
    private val encoder: QrCodeEncoder,
) : GenerateQrCode {
    @Async
    override fun generate(shortUrl: String): CompletableFuture<ByteArray> = CompletableFuture.completedFuture(encoder.encode(shortUrl))
}
