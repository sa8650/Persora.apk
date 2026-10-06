package app.persora.android.alarms

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.persora.android.PersoraApp
import app.persora.android.R

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = AlarmScheduler(context)
        when (intent.action) {
            ACTION_FIRE -> {
                val entry = scheduler.decode(intent.getStringExtra(EXTRA_ENTRY)) ?: return
                runCatching { if (entry.kind == "alarm") ring(context, entry) else remind(context, entry) }
                    .onFailure { android.util.Log.w("Persora", "Could not present schedule ${entry.id}", it) }
                // Mirror the web: a fired reminder / alarm becomes a `schedule:` notification under the bell.
                (context.applicationContext as? PersoraApp)?.container?.vault?.recordScheduleFired(entry.id, entry.title, entry.kind, entry.at)
                // Repeating alarms: plan the next occurrence from the latest cached vault copy (the one that just fired is now in the past).
                (context.applicationContext as? PersoraApp)?.container?.let { c -> runCatching { scheduler.sync(c.vault.items.value, afterMillis = System.currentTimeMillis() + 60_000) } }
            }
            ACTION_SNOOZE -> {
                val entry = scheduler.decode(intent.getStringExtra(EXTRA_ENTRY)) ?: return
                context.stopService(Intent(context, AlarmSoundService::class.java))
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(entry.id.hashCode())
                scheduler.snooze(entry, 10)
                (context.applicationContext as? PersoraApp)?.container?.vault?.launch { snoozeOnServer(context, entry.id) }
            }
            ACTION_DISMISS -> {
                val entry = scheduler.decode(intent.getStringExtra(EXTRA_ENTRY))
                context.stopService(Intent(context, AlarmSoundService::class.java))
                entry?.let { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(it.id.hashCode()) }
            }
        }
    }

    private suspend fun snoozeOnServer(context: Context, id: String) {
        val vault = (context.applicationContext as? PersoraApp)?.container?.vault ?: return
        val item = vault.items.value.firstOrNull { it.id == id } ?: return
        vault.snooze(item, java.time.Instant.now().plusSeconds(600).toString())
    }

    private fun remind(context: Context, entry: AlarmScheduler.Scheduled) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(context, entry.id.hashCode(), context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply { putExtra("open", "notes"); putExtra("itemId", entry.id) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, AlarmScheduler.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Reminder is due").setContentText(entry.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(entry.title, entry.details).filter { it.isNotBlank() }.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true).setContentIntent(open)
            .addAction(0, "Snooze 10 min", action(context, ACTION_SNOOZE, entry)).addAction(0, "Done", action(context, ACTION_DISMISS, entry))
            .build()
        nm.notify(entry.id.hashCode(), notification)
    }

    private fun ring(context: Context, entry: AlarmScheduler.Scheduled) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fullScreen = PendingIntent.getActivity(context, entry.id.hashCode(), AlarmRingingActivity.intent(context, entry), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, AlarmScheduler.CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Alarm is ringing").setContentText(entry.title)
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setOngoing(true).setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true).setContentIntent(fullScreen).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Snooze 10 min", action(context, ACTION_SNOOZE, entry)).addAction(0, "Dismiss", action(context, ACTION_DISMISS, entry))
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        nm.notify(entry.id.hashCode(), notification)
        // Sound + vibration run in a foreground service; if the OS refuses to start it, fall back to a plain ringtone so the alarm is still heard.
        try { AlarmSoundService.start(context, entry) } catch (e: Exception) {
            android.util.Log.w("Persora", "Alarm sound service failed, using fallback ringtone", e)
            runCatching { android.media.RingtoneManager.getRingtone(context, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))?.play() }
        }
    }

    private fun action(context: Context, action: String, entry: AlarmScheduler.Scheduled): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply { this.action = action; data = android.net.Uri.parse("persora://schedule/${entry.id}/$action"); putExtra(EXTRA_ENTRY, kotlinx.serialization.json.Json.encodeToString(AlarmScheduler.Scheduled.serializer(), entry)) }
        return PendingIntent.getBroadcast(context, (entry.id + action).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_FIRE = "app.persora.android.SCHEDULE_FIRE"
        const val ACTION_SNOOZE = "app.persora.android.SCHEDULE_SNOOZE"
        const val ACTION_DISMISS = "app.persora.android.SCHEDULE_DISMISS"
        const val EXTRA_ENTRY = "entry"
    }
}
