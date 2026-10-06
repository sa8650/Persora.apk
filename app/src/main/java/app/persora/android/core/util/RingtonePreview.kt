package app.persora.android.core.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.persora.android.BuildConfig
import app.persora.android.appContainer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** RingtonePreviewButton on the web: play the selected tone for a few seconds while editing a reminder/alarm. */
object RingtonePreview {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stop() }
    @Volatile var playingId: String? = null
        private set

    fun toggle(context: Context, ringtoneId: String, durationMs: Long = 5_000, onStateChanged: (String?) -> Unit) {
        if (playingId == ringtoneId) { stop(); onStateChanged(null); return }
        play(context, ringtoneId, durationMs) { onStateChanged(null) }
        onStateChanged(ringtoneId)
    }

    fun play(context: Context, ringtoneId: String, durationMs: Long = 5_000, onFinished: () -> Unit = {}) {
        stop()
        val app = context.applicationContext
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val mp = MediaPlayer().apply { setAudioAttributes(attributes); isLooping = true }
        player = mp; playingId = ringtoneId
        val finish = { handler.removeCallbacks(stopRunnable); stop(); onFinished() }
        try {
            if (ringtoneId.startsWith("builtin-")) {
                mp.setDataSource(app, builtinUri(app, ringtoneId))
            } else {
                val url = "${BuildConfig.API_BASE_URL}/alarm-ringtones/${Uri.encode(ringtoneId)}/audio"
                val cookie = url.toHttpUrlOrNull()?.let { app.appContainer.cookieJar.loadForRequest(it) }?.joinToString("; ") { "${it.name}=${it.value}" }.orEmpty()
                mp.setDataSource(app, Uri.parse(url), if (cookie.isBlank()) emptyMap() else mapOf("Cookie" to cookie))
            }
            mp.setOnPreparedListener { it.start(); handler.postDelayed({ finish() }, durationMs) }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.prepareAsync()
        } catch (_: Exception) { finish() }
    }

    /** Built-in Persora tones map to system sounds on Android: soft chime → notification tone, pulse → alarm tone. */
    fun builtinUri(context: Context, ringtoneId: String): Uri =
        if (ringtoneId == "builtin-pulse") RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        else RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        runCatching { player?.stop(); player?.release() }
        player = null; playingId = null
    }
}
