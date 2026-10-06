package app.persora.android.core.network

import app.persora.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Same-origin Pages Functions client: every call hits https://persora.pages.dev/api/..., the exact
 * API the website uses, so both clients share one Supabase database and one private R2 bucket.
 * Browser-visible behavior is mirrored: `cache: no-store`, JSON bodies, `{ error }` surfaced as ApiException.
 */
class ApiClient(cookieJar: PersistentCookieJar, val baseUrl: String = BuildConfig.API_BASE_URL) {

    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false; encodeDefaults = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("Accept", "application/json, */*")
                .header("Cache-Control", "no-store")
                .header("User-Agent", "Persora-Android/${BuildConfig.VERSION_NAME}")
                // Pages Functions derive the allowed origin from the request; sending the web origin keeps CORS happy.
                .header("Origin", BuildConfig.WEB_ORIGIN)
                .build())
        }
        .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC; redactHeader("Cookie"); redactHeader("Set-Cookie") }) }
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun url(path: String) = baseUrl + path

    suspend fun getRaw(path: String): Response = execute(Request.Builder().url(url(path)).get().build())
    suspend fun get(path: String): JsonElement = parse(getRaw(path))
    suspend fun post(path: String, body: JsonElement? = null): JsonElement = parse(execute(Request.Builder().url(url(path)).post((body ?: JsonObject(emptyMap())).toString().toRequestBody(jsonType)).build()))
    suspend fun patch(path: String, body: JsonElement? = null): JsonElement = parse(execute(Request.Builder().url(url(path)).patch((body ?: JsonObject(emptyMap())).toString().toRequestBody(jsonType)).build()))
    suspend fun delete(path: String, body: JsonElement? = null): JsonElement {
        val builder = Request.Builder().url(url(path))
        if (body != null) builder.delete(body.toString().toRequestBody(jsonType)) else builder.delete()
        return parse(execute(builder.build()))
    }

    /** multipart/form-data upload with a `file` field — same shape the web's uploadMultipart() sends. */
    suspend fun upload(
        path: String,
        fileName: String,
        mimeType: String,
        size: Long,
        openStream: () -> InputStream,
        extraFields: Map<String, String> = emptyMap(),
        onProgress: ((loaded: Long, total: Long) -> Unit)? = null,
    ): JsonElement {
        val fileBody = object : RequestBody() {
            override fun contentType() = (mimeType.ifBlank { "application/octet-stream" }).toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                openStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        sent += read
                        onProgress?.invoke(sent, size)
                    }
                }
            }
        }
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            extraFields.forEach { (key, value) -> addFormDataPart(key, value) }
            addFormDataPart("file", fileName, fileBody)
        }.build()
        return parse(execute(Request.Builder().url(url(path)).post(multipart).build()))
    }

    private suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        val response = try { http.newCall(request).execute() } catch (error: IOException) {
            // Transport failure (no network, DNS, TLS, timeout): status 0 so callers treat it as offline; the raw cause stays in the log only.
            android.util.Log.w("Persora", "Network error for ${request.url.encodedPath}: ${error.message}")
            throw ApiException("You appear to be offline.", 0)
        }
        if (!response.isSuccessful) {
            val message = runCatching {
                val text = response.body?.string().orEmpty()
                json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull() ?: "Persora service returned ${response.code}."
            response.close()
            throw ApiException(message, response.code)
        }
        response
    }

    private fun parse(response: Response): JsonElement = response.use {
        val text = it.body?.string().orEmpty()
        if (text.isBlank()) JsonNull else runCatching { json.parseToJsonElement(text) }.getOrElse { JsonNull }
    }
}
