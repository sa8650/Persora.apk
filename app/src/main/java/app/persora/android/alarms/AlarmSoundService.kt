package app.persora.android.alarms

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import app.persora.android.PersoraApp
import app.persora.android.R
import app.persora.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Loops the chosen ringtone while an alarm rings. Built-in tones map to the device alarm sound; admin-uploaded
 *  tones stream from GET /api/alarm-ringtones/{id}/audio with the session cookie. */
class AlarmSoundService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val entry = AlarmScheduler(this).decode(intent?.getStringExtra(ScheduleReceiver.EXTRA_ENTRY))
        val notification = NotificationCompat.Builder(this, AlarmScheduler.CHANNEL_ALARMS).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alarm is ringing").setContentText(entry?.title ?: "Persora alarm").setOngoing(true).setCategory(NotificationCompat.CATEGORY_ALARM).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(9001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(9001, notification)
        play(entry?.ringtoneId ?: "builtin-soft")
        vibrate()
        return START_NOT_STICKY
    }

    private fun play(ringtoneId: String) {
        stopPlayer()
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        player = MediaPlayer().apply {
            setAudioAttributes(attributes); isLooping = true
            try {
                if (ringtoneId.startsWith("builtin-")) {
                    setDataSource(this@AlarmSoundService, app.persora.android.core.util.RingtonePreview.builtinUri(this@AlarmSoundService, ringtoneId))
                } else {
                    val container = (application as? PersoraApp)?.container
                    val url = "${BuildConfig.API_BASE_URL}/alarm-ringtones/${Uri.encode(ringtoneId)}/audio"
                    val cookie = url.toHttpUrlOrNull()?.let { httpUrl -> container?.cookieJar?.loadForRequest(httpUrl) }?.joinToString("; ") { "${it.name}=${it.value}" }.orEmpty()
                    setDataSource(this@AlarmSoundService, Uri.parse(url), if (cookie.isBlank()) emptyMap() else mapOf("Cookie" to cookie))
                }
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> fallback(); true }
                prepareAsync()
            } catch (_: Exception) { fallback() }
        }
    }

    private fun fallback() {
        stopPlayer()
        player = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply { isLooping = true; start() }
    }

    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 1200), 0))
    }

    private fun stopPlayer() { runCatching { player?.stop(); player?.release() }; player = null }

    override fun onDestroy() { stopPlayer(); vibrator?.cancel(); super.onDestroy() }

    companion object {
        fun start(context: Context, entry: AlarmScheduler.Scheduled) {
            val intent = Intent(context, AlarmSoundService::class.java).putExtra(ScheduleReceiver.EXTRA_ENTRY, kotlinx.serialization.json.Json.encodeToString(AlarmScheduler.Scheduled.serializer(), entry))
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
        fun stop(context: Context) = context.stopService(Intent(context, AlarmSoundService::class.java))
    }
}
