package es.unizar.urlshortener.links.adapters.web

import es.unizar.urlshortener.links.application.ClientInfo
import org.springframework.stereotype.Component
import ua_parser.Parser

@Component
class ClientDetector {
    // Se cargan las reglas una vez y se reutilizan en las siguientes peticiones
    private val parser = Parser()

    fun detect(userAgent: String?): ClientInfo {
        // Si no se recibe la cabecera se dejan las dos familias como desconocidas
        if (userAgent.isNullOrBlank()) return ClientInfo()

        // Se obtiene cada familia por separado porque puede reconocerse solo una de las dos
        val client = parser.parse(userAgent)
        return ClientInfo(family(client.userAgent.family), family(client.os.family))
    }

    // Se usa el mismo nombre cuando la librería devuelve Other o no encuentra una familia
    private fun family(name: String?): String = if (name.isNullOrBlank() || name == "Other") "Unknown" else name
}
