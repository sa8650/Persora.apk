package app.persora.android.alarms

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import app.persora.android.core.util.Dates
import app.persora.android.data.model.VaultItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mirrors the web's in-app scheduler (reminderAt / alarmTime + repeatDays / snoozedUntil metadata) but uses
 * AlarmManager so reminders and alarms fire even when Persora is in the background or the phone is asleep.
 */
class AlarmScheduler(private val context: Context) {

    @Serializable
    data class Scheduled(val id: String, val title: String, val kind: String, val at: Long, val ringtoneId: String, val ringtoneName: String, val details: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs get() = context.getSharedPreferences("persora_schedule", Context.MODE_PRIVATE)
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Re-plans every enabled reminder/alarm from the latest server copy. Called after each refresh / save. */
    fun sync(items: List<VaultItem>, afterMillis: Long = System.currentTimeMillis() - 60_000) {
        val previous = load()
        previous.forEach { cancel(it.id) }
        val next = items.filter { it.isSchedule && it.metadata["enabled"] != "false" }.mapNotNull { item ->
            val from = java.time.Instant.ofEpochMilli(afterMillis).atZone(java.time.ZoneId.systemDefault())
            val floor = java.time.ZonedDateTime.now().minusMinutes(1)
            val at = Dates.nextScheduleDate(item, now = if (from.isAfter(floor)) from else floor)?.toInstant()?.toEpochMilli() ?: return@mapNotNull null
            if (at < afterMillis) return@mapNotNull null
            Scheduled(item.id, item.title, item.recordType, at, item.metadata["ringtoneId"] ?: "builtin-soft", item.metadata["ringtoneName"] ?: "", item.metadata["todoDetails"].orEmpty())
        }
        next.forEach { schedule(it) }
        save(next)
    }

    fun schedule(entry: Scheduled) {
        val pending = pendingIntent(entry)
        val at = maxOf(entry.at, System.currentTimeMillis() + 1_000)
        try {
            if (canScheduleExact()) alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppIntent()), pending)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
    }

    fun cancel(id: String) { alarmManager.cancel(pendingIntent(Scheduled(id, "", "", 0, "", "", ""))) }

    fun rescheduleAll() { load().forEach { if (it.at > System.currentTimeMillis() - 60_000) schedule(it) } }

    fun snooze(entry: Scheduled, minutes: Int = 10) {
        val snoozed = entry.copy(at = System.currentTimeMillis() + minutes * 60_000L)
        schedule(snoozed); save(load().filterNot { it.id == entry.id } + snoozed)
    }

    private fun pendingIntent(entry: Scheduled): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_FIRE; data = android.net.Uri.parse("persora://schedule/${entry.id}")
            putExtra(ScheduleReceiver.EXTRA_ENTRY, json.encodeToString(entry))
        }
        return PendingIntent.getBroadcast(context, entry.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(context, 7001, context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply { putExtra("open", "notes") }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun load(): List<Scheduled> = prefs.getString("entries", null)?.let { runCatching { json.decodeFromString<List<Scheduled>>(it) }.getOrNull() } ?: emptyList()
    private fun save(entries: List<Scheduled>) = prefs.edit().putString("entries", json.encodeToString(entries)).apply()
    fun decode(raw: String?): Scheduled? = raw?.let { runCatching { json.decodeFromString<Scheduled>(it) }.getOrNull() }

    companion object {
        const val CHANNEL_REMINDERS = "persora_reminders"
        const val CHANNEL_ALARMS = "persora_alarms"
        const val CHANNEL_SHARING = "persora_sharing"

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminder times you set in Tasks & Notes"; enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audio)
            })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarms ring full screen with the ringtone you chose"; enableVibration(true); setSound(null, null); setBypassDnd(false)
            })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_SHARING, "Sharing activity", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Documents shared with you and permission changes" })
        }
    }
}
