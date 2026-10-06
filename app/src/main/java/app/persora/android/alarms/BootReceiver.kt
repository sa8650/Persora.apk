package app.persora.android.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager alarms do not survive a reboot; re-plan from the persisted schedule. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.ensureChannels(context)
        AlarmScheduler(context).rescheduleAll()
    }
}
