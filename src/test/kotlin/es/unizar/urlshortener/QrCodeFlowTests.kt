package es.unizar.urlshortener

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.jayway.jsonpath.JsonPath.read
import es.unizar.urlshortener.clicks.ClickLoggedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class QrCodeFlowTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var events: ApplicationEvents

    @Test
    fun `pedir un QR devuelve un PNG con la URL corta y no registra clics`() {
        val hash = createLink()

        val initial =
            mockMvc
                .perform(get("/$hash").accept(MediaType.IMAGE_PNG))
                .andExpect(request().asyncStarted())
                .andReturn()

        val result =
            mockMvc
                .perform(asyncDispatch(initial))
                .andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
                .andReturn()

        val image = ImageIO.read(ByteArrayInputStream(result.response.contentAsByteArray))
        assertNotNull(image)
        val bitmap = BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(image)))
        assertEquals("http://localhost/$hash", MultiFormatReader().decode(bitmap).text)
        assertEquals(0, events.stream(ClickLoggedEvent::class.java).filter { it.hash == hash }.count())
    }

    @Test
    fun `una peticion normal sigue redirigiendo a la URL original`() {
        val hash = createLink()

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/qr"))
    }

    @Test
    fun `otras cabeceras Accept siguen usando la redireccion normal`() {
        val hash = createLink()

        for (accept in listOf(MediaType.ALL, MediaType.TEXT_HTML)) {
            mockMvc
                .perform(get("/$hash").accept(accept))
                .andExpect(status().isTemporaryRedirect)
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/qr"))
        }
    }

    @Test
    fun `pedir el QR de una URL inexistente devuelve 404 sin imagen`() {
        val result =
            mockMvc
                .perform(get("/unknown-qr").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
                .andReturn()

        assertNotEquals(MediaType.IMAGE_PNG_VALUE, result.response.contentType?.toString())
    }

    @Test
    fun `pedir el QR de una URL desactivada devuelve 410 sin imagen`() {
        val (hash, key) = createManagedLink()
        mockMvc
            .perform(delete("/api/managed-links/$hash").header(HttpHeaders.AUTHORIZATION, "Bearer $key"))
            .andExpect(status().isNoContent)

        val result =
            mockMvc
                .perform(get("/$hash").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isGone)
                .andExpect(jsonPath("$.status").value(410))
                .andReturn()

        assertNotEquals(MediaType.IMAGE_PNG_VALUE, result.response.contentType?.toString())
    }

    private fun createLink(): String {
        val result =
            mockMvc
                .perform(
                    post("/api/link")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("url", "https://example.com/qr"),
                ).andExpect(status().isCreated)
                .andReturn()
        return read<String>(result.response.contentAsString, "$.hash")
    }

    private fun createManagedLink(): Pair<String, String> {
        val result =
            mockMvc
                .perform(
                    post("/api/managed-links")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("url", "https://example.com/managed-qr"),
                ).andExpect(status().isCreated)
                .andReturn()
        return read<String>(result.response.contentAsString, "$.hash") to
            read<String>(result.response.contentAsString, "$.managementKey")
    }
}
