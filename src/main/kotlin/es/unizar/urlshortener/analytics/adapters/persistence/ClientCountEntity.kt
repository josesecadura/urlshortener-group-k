package es.unizar.urlshortener.analytics.adapters.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

// Se usa el enlace junto con las dos familias para identificar cada contador
data class ClientCountId(
    var hash: String = "",
    var browser: String = "",
    var platform: String = "",
) : Serializable {
    private companion object {
        const val serialVersionUID = 1L
    }
}

@Entity
@Table(name = "client_count")
@IdClass(ClientCountId::class)
class ClientCountEntity(
    @Id var hash: String,
    @Id var browser: String,
    @Id var platform: String,
    // Se guarda una fila por pareja aunque se repita en muchos clics
    var clicks: Long,
)
