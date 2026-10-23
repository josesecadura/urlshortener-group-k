package es.unizar.urlshortener.links.adapters.web

import es.unizar.urlshortener.links.application.CreateManagedShortUrl
import es.unizar.urlshortener.links.application.InvalidUrlException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI

data class ManagedShortUrlResponse(
    val url: String,
    val hash: String,
    val managementKey: String,
)

@RestController
class ManagedLinkController(
    private val createManagedShortUrl: CreateManagedShortUrl,
) {
    @PostMapping(
        "/api/managed-links",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun create(
        @RequestParam url: String,
    ): ResponseEntity<ManagedShortUrlResponse> {
        val created = createManagedShortUrl.createManaged(url)
        val location =
            ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/{hash}")
                .buildAndExpand(created.hash)
                .toUri()
        return ResponseEntity
            .created(location)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(
                ManagedShortUrlResponse(
                    url = location.toString(),
                    hash = created.hash,
                    managementKey = created.managementKey,
                ),
            )
    }

    @ExceptionHandler(InvalidUrlException::class, IllegalArgumentException::class)
    fun badRequest(ex: RuntimeException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid URL")
        problem.title = "Invalid URL"
        problem.type = URI.create("about:blank")
        problem.instance = URI.create("/api/managed-links")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }
}
