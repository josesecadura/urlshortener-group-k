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
        // Generamos una clave aleatoria diferente para cada URL administrable
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedManagementKey(plaintext = plaintext, sha256 = digest(plaintext))
    }

    fun matches(
        storedDigest: String,
        candidateKey: String,
    ): Boolean {
        // Comparamos los hashes para no necesitar guardar la clave original
        val candidateDigest = digest(candidateKey)
        return MessageDigest.isEqual(
            storedDigest.toByteArray(StandardCharsets.US_ASCII),
            candidateDigest.toByteArray(StandardCharsets.US_ASCII),
        )
    }

    fun digest(plaintext: String): String =
        HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(plaintext.toByteArray(StandardCharsets.UTF_8)),
        )
}
