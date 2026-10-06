package app.persora.android.core.network

import app.persora.android.core.storage.SecurePrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * The Pages API authenticates with an HttpOnly `persora_session` cookie scoped to `/api` (not a bearer
 * token). This jar persists it in Keystore-encrypted preferences and replays it on every API call.
 */
class PersistentCookieJar(private val prefs: SecurePrefs) : CookieJar {

    @Serializable
    private data class StoredCookie(val name: String, val value: String, val domain: String, val path: String, val expiresAt: Long, val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean)

    private val json = Json { ignoreUnknownKeys = true }
    private val cookies = mutableMapOf<String, StoredCookie>()

    init { load() }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val key = "${cookie.domain}|${cookie.path}|${cookie.name}"
            if (cookie.expiresAt < System.currentTimeMillis() || cookie.value.isBlank()) this.cookies.remove(key)
            else this.cookies[key] = StoredCookie(cookie.name, cookie.value, cookie.domain, cookie.path, cookie.expiresAt, cookie.secure, cookie.httpOnly, cookie.hostOnly)
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val expired = cookies.filterValues { it.expiresAt < now }.keys
        if (expired.isNotEmpty()) { expired.forEach { cookies.remove(it) }; persist() }
        return cookies.values.mapNotNull { it.toCookie() }.filter { it.matches(url) }
    }

    @Synchronized
    fun hasSession(): Boolean = cookies.values.any { it.name == "persora_session" && it.expiresAt > System.currentTimeMillis() }

    @Synchronized
    fun clear() { cookies.clear(); prefs.remove(SecurePrefs.KEY_COOKIES) }

    private fun StoredCookie.toCookie(): Cookie? = runCatching {
        Cookie.Builder().name(name).value(value).path(path).expiresAt(expiresAt).apply {
            if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            if (secure) secure()
            if (httpOnly) httpOnly()
        }.build()
    }.getOrNull()

    private fun persist() { prefs.putString(SecurePrefs.KEY_COOKIES, json.encodeToString(cookies.values.toList())) }

    private fun load() {
        val raw = prefs.getString(SecurePrefs.KEY_COOKIES) ?: return
        runCatching { json.decodeFromString<List<StoredCookie>>(raw) }.getOrNull()?.forEach { cookies["${it.domain}|${it.path}|${it.name}"] = it }
    }
}
