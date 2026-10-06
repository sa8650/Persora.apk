package app.persora.android.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.util.Locale

data class PickedFile(val uri: Uri, val name: String, val size: Long, val mime: String) {
    fun open(context: Context): InputStream = context.contentResolver.openInputStream(uri) ?: error("Cannot read the selected file.")
}

object Files {
    fun describe(context: Context, uri: Uri): PickedFile {
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) ?: name }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = cursor.getLong(it) }
            }
        }
        if (size <= 0) size = runCatching { context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L }.getOrDefault(0L)
        val mime = context.contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT)) ?: "application/octet-stream"
        return PickedFile(uri, name, size, mime)
    }

    fun formatSize(bytes: Long?): String {
        val b = bytes ?: return ""
        if (b < 1024) return "$b B"
        val kb = b / 1024.0; if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0; if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    /** Writes a private download into the app cache and returns a shareable content:// URI. */
    fun stash(context: Context, name: String, input: InputStream): Uri {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "persora-file" }
        val file = File(dir, safe)
        file.outputStream().use { out -> input.copyTo(out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Target for ActivityResultContracts.TakePicture(); lives in cache so it is cleaned up automatically. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun viewIntent(uri: Uri, mime: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime.ifBlank { "*/*" }); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun shareIntent(uri: Uri, mime: String, title: String): Intent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = mime.ifBlank { "*/*" }; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, title); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, title)
    fun isImage(mime: String?) = mime?.startsWith("image/") == true
    fun isPdf(mime: String?, name: String? = null) = mime == "application/pdf" || name?.lowercase()?.endsWith(".pdf") == true
    private val TEXT_EXT = setOf("txt", "md", "markdown", "csv", "tsv", "json", "log", "xml", "yml", "yaml", "ini", "cfg", "conf", "rtf", "srt", "vcf", "ics", "kt", "java", "js", "ts", "py", "html", "htm", "css", "sql")
    fun isText(mime: String?, name: String? = null): Boolean {
        val m = mime.orEmpty().lowercase()
        if (m.startsWith("text/") || m in setOf("application/json", "application/xml", "application/x-yaml", "application/csv", "application/rtf", "application/x-sh")) return true
        return name?.substringAfterLast('.', "")?.lowercase() in TEXT_EXT
    }

    /** Preview downloads are cached per file key so re-opening the drawer doesn't hit the network again. */
    fun previewCacheFile(context: Context, key: String, name: String): File {
        val dir = File(context.cacheDir, "previews").apply { mkdirs() }
        val ext = name.substringAfterLast('.', "").take(6).filter { it.isLetterOrDigit() }
        return File(dir, key.hashCode().toUInt().toString(16) + (if (ext.isNotBlank()) ".$ext" else ""))
    }
}
