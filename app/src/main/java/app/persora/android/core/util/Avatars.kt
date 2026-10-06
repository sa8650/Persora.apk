package app.persora.android.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/** Port of encodeProfilePhoto() in Workspace.tsx: square-crop, downscale and JPEG-encode until the data URL fits the API's 80 000-char limit. */
object Avatars {
    private const val MAX_DATA_URL_CHARS = 80_000
    private const val MAX_SOURCE_BYTES = 8L * 1024 * 1024

    fun encodeProfilePhoto(context: Context, uri: Uri, size: Long, mime: String): String {
        if (mime !in setOf("image/jpeg", "image/png", "image/webp")) error("Choose a JPG, PNG, or WEBP photo.")
        if (size > MAX_SOURCE_BYTES) error("Profile photos must be 8 MB or smaller.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Couldn't read that photo.")
        val rotation = runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0 }.getOrDefault(0)
        val upright = if (rotation != 0) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true) else decoded
        val side = minOf(upright.width, upright.height)
        val square = Bitmap.createBitmap(upright, (upright.width - side) / 2, (upright.height - side) / 2, side, side)
        for (target in listOf(256, 192, 160, 128, 96)) {
            val scaled = Bitmap.createScaledBitmap(square, target, target, true)
            for (quality in listOf(85, 75, 65, 55, 45)) {
                val out = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (dataUrl.length <= MAX_DATA_URL_CHARS) return dataUrl
            }
        }
        error("That photo is too detailed to store. Try a simpler image.")
    }
}
