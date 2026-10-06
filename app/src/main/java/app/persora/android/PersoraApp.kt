package app.persora.android

import android.app.Application
import android.content.Context
import app.persora.android.alarms.AlarmScheduler
import app.persora.android.core.network.ApiClient
import app.persora.android.core.network.PersistentCookieJar
import app.persora.android.core.storage.JsonCache
import app.persora.android.core.storage.SecurePrefs
import app.persora.android.data.api.PersoraApi
import app.persora.android.data.repository.SessionManager
import app.persora.android.data.repository.VaultRepository
import coil.ImageLoader
import coil.ImageLoaderFactory

/** Hand-rolled DI: one container for the whole app (small enough not to need Hilt). */
class AppContainer(context: Context) {
    val prefs = SecurePrefs(context)
    val cookieJar = PersistentCookieJar(prefs)
    val client = ApiClient(cookieJar)
    val api = PersoraApi(client)
    val cache = JsonCache(context)
    val session = SessionManager(api, cookieJar, prefs, cache)
    val scheduler = AlarmScheduler(context)
    val vault = VaultRepository(api, cache, session, onScheduleChanged = { items -> runCatching { scheduler.sync(items) } })
}

class PersoraApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmScheduler.ensureChannels(this)
        app.persora.android.calls.PersoraInCallService.ensureChannel(this)
    }

    /** Coil shares the API's OkHttp client so private photos (/contacts/photo, /file) load with the session cookie. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).okHttpClient { container.client.http }.components { add(coil.decode.SvgDecoder.Factory()) }.crossfade(true).respectCacheHeaders(false).build()
}

val Context.appContainer: AppContainer get() = (applicationContext as PersoraApp).container
