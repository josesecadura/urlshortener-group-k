package es.unizar.urlshortener.links

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import es.unizar.urlshortener.links.application.GenerateQrCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@SpringBootTest
class QrCodeServiceTests {
    @Autowired
    lateinit var generateQrCode: GenerateQrCode

    @Test
    fun `generates a PNG that encodes the requested short URL`() {
        val shortUrl = "https://short.example/abc123"

        val png = generateQrCode.generate(shortUrl).get(5, TimeUnit.SECONDS)

        val image = ImageIO.read(ByteArrayInputStream(png))
        assertNotNull(image)
        val bitmap = BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(image)))
        assertEquals(shortUrl, MultiFormatReader().decode(bitmap).text)
    }
}
