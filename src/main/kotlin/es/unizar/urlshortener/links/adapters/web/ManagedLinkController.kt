package es.unizar.urlshortener.links.adapters.web

import es.unizar.urlshortener.links.application.CreateManagedShortUrl
import es.unizar.urlshortener.links.application.DeactivateManagedShortUrl
import es.unizar.urlshortener.links.application.InvalidManagementKeyException
import es.unizar.urlshortener.links.application.InvalidUrlException
import es.unizar.urlshortener.links.application.LinkNotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
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
    private val deactivateManagedShortUrl: DeactivateManagedShortUrl,
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

    @DeleteMapping("/api/managed-links/{hash}")
    fun deactivate(
        @PathVariable hash: String,
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ResponseEntity<Void> {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw InvalidManagementKeyException()
        }
        val key = authorization.removePrefix("Bearer ")
        if (key.isBlank()) {
            throw InvalidManagementKeyException()
        }
        deactivateManagedShortUrl.deactivate(hash, key)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(InvalidManagementKeyException::class)
    fun forbidden(ex: InvalidManagementKeyException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Invalid management key")
        problem.title = "Forbidden"
        problem.type = URI.create("about:blank")
        problem.instance = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem)
    }

    @ExceptionHandler(LinkNotFoundException::class)
    fun notFound(ex: LinkNotFoundException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Unknown link")
        problem.title = "Not Found"
        problem.type = URI.create("about:blank")
        problem.instance = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
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
