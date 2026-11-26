package es.unizar.urlshortener.links.adapters.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import es.unizar.urlshortener.links.application.QrCodeEncoder
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

@Component
class ZxingQrCodeEncoder : QrCodeEncoder {
    override fun encode(content: String): ByteArray {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE)
        return ByteArrayOutputStream().use { output ->
            MatrixToImageWriter.writeToStream(matrix, PNG_FORMAT, output)
            output.toByteArray()
        }
    }

    private companion object {
        const val SIZE = 256
        const val PNG_FORMAT = "PNG"
    }
}
