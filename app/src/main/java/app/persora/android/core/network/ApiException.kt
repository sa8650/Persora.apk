package app.persora.android.core.network

/** Mirrors PagesApiError from src/lib/cloud.ts: the server's `{ error }` message plus HTTP status. */
class ApiException(message: String, val status: Int) : Exception(message) {
    val isUnauthorized: Boolean get() = status == 401
    val isConflict: Boolean get() = status == 409
    val isOffline: Boolean get() = status == 0
}
