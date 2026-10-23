package es.unizar.urlshortener.links.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

internal data class IssuedManagementKey(
    val plaintext: String,
    val sha256: String,
)

/** Capability keys are random; only their digest is persisted. */
internal object ManagementKeys {
    private val random = SecureRandom()

    fun issue(): IssuedManagementKey {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedManagementKey(plaintext = plaintext, sha256 = digest(plaintext))
    }

    fun digest(plaintext: String): String =
        HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(plaintext.toByteArray(StandardCharsets.UTF_8)),
        )
}
