package es.unizar.urlshortener.links.adapters.web

import es.unizar.urlshortener.links.application.CreateShortUrl
import es.unizar.urlshortener.links.application.GenerateQrCode
import es.unizar.urlshortener.links.application.InvalidUrlException
import es.unizar.urlshortener.links.application.LinkNotFoundException
import es.unizar.urlshortener.links.application.RedirectShortUrl
import es.unizar.urlshortener.links.application.ResolveShortUrl
import es.unizar.urlshortener.links.application.UrlDeactivatedException
import es.unizar.urlshortener.links.application.UrlExpiredException
import jakarta.validation.constraints.Future
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI
import java.time.Instant
import java.util.concurrent.CompletableFuture

data class ShortUrlResponse(
    val url: String,
    val hash: String,
)

@RestController
class LinkController(
    private val createShortUrl: CreateShortUrl,
    private val redirectShortUrl: RedirectShortUrl,
    private val resolveShortUrl: ResolveShortUrl,
    private val generateQrCode: GenerateQrCode,
) {
    @PostMapping(
        "/api/link",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun create(
        @RequestParam url: String,
        @RequestParam(required = false) @Future expirationTime: Instant?,
        // se utiliza @Future para asegurarse de que la fecha de expiracion sea posterior al momento de añadir a la BD
    ): ResponseEntity<ShortUrlResponse> {
        val created = createShortUrl.create(url, expirationTime)
        val location = shortUrl(created.hash)
        return ResponseEntity.created(location).body(
            ShortUrlResponse(url = location.toString(), hash = created.hash),
        )
    }

    @GetMapping("/{hash}")
    fun redirect(
        @PathVariable hash: String,
    ): ResponseEntity<Void> {
        val redirection = redirectShortUrl.redirect(hash)
        val headers = HttpHeaders()
        headers.location = URI.create(redirection.target)
        return ResponseEntity(headers, HttpStatus.TEMPORARY_REDIRECT)
    }

    @GetMapping(
        value = ["/{hash}"],
        headers = ["Accept=image/png"],
        produces = [MediaType.IMAGE_PNG_VALUE],
    )
    fun qrCode(
        @PathVariable hash: String,
    ): CompletableFuture<ResponseEntity<ByteArray>> {
        resolveShortUrl.resolve(hash)
        return generateQrCode.generate(shortUrl(hash).toString()).thenApply { png ->
            ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png)
        }
    }

    private fun shortUrl(hash: String): URI =
        ServletUriComponentsBuilder
            .fromCurrentContextPath()
            .path("/{hash}")
            .buildAndExpand(hash)
            .toUri()

    @ExceptionHandler(InvalidUrlException::class)
    fun invalidUrl(ex: InvalidUrlException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "invalid url")))

    @ExceptionHandler(LinkNotFoundException::class)
    fun notFound(ex: LinkNotFoundException): ResponseEntity<ProblemDetail> {
        val detail = ex.message ?: "not found"
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail)
        problem.title = "Not Found"
        problem.type = URI.create("about:blank")
        problem.instance = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()
        problem.setProperty("error", detail)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    // Respondemos con 410 para diferenciar una URL desactivada de una inexistente
    @ExceptionHandler(UrlDeactivatedException::class)
    fun deactivated(ex: UrlDeactivatedException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.message ?: "Link deactivated")
        problem.title = "Gone"
        problem.type = URI.create("about:blank")
        problem.instance = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()
        return ResponseEntity.status(HttpStatus.GONE).body(problem)
    }

    @ExceptionHandler(UrlExpiredException::class)
    fun expiredUrl(): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.GONE).build()
}
