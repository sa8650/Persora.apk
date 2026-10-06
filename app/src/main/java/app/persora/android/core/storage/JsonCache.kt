package app.persora.android.core.storage

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Tiny per-user offline cache for the last successful JSON response of each GET endpoint, so the
 * vault still opens on a train. Files live in app-private storage and are wiped on sign-out.
 */
class JsonCache(context: Context) {
    private val dir = File(context.filesDir, "persora-cache").apply { mkdirs() }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    fun read(key: String): String? = fileFor(key).takeIf { it.exists() }?.readText()
    fun write(key: String, body: String) { runCatching { fileFor(key).writeText(body) } }
    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}
