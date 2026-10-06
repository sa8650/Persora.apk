package app.persora.android.core.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Membership, saved-link, contact and business-card QR codes are generated locally, like the web. */
object Qr {
    fun render(text: String, size: Int = 640): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) pixels[y * size + x] = if (matrix[x, y]) 0xFF202124.toInt() else Color.WHITE
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /** vCard 3.0 for a contact / business card so phones can import it from the QR. */
    fun vCard(name: String, phones: List<String>, email: String, org: String, title: String, address: String, url: String = ""): String = buildString {
        appendLine("BEGIN:VCARD"); appendLine("VERSION:3.0"); appendLine("FN:$name"); appendLine("N:$name;;;;")
        if (org.isNotBlank()) appendLine("ORG:$org"); if (title.isNotBlank()) appendLine("TITLE:$title")
        phones.forEach { appendLine("TEL;TYPE=CELL:$it") }
        if (email.isNotBlank()) appendLine("EMAIL:$email"); if (address.isNotBlank()) appendLine("ADR;TYPE=HOME:;;$address;;;;"); if (url.isNotBlank()) appendLine("URL:$url")
        appendLine("END:VCARD")
    }
}
