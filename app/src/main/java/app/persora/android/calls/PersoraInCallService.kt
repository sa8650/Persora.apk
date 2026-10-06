package app.persora.android.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import app.persora.android.R

/**
 * Receives calls from Telecom once Persora is the default phone app and shows [InCallActivity] — Persora's own
 * in-call screen — instead of the system dialer. Ringing audio stays with the system (IN_CALL_SERVICE_RINGING=false).
 */
class PersoraInCallService : InCallService() {
    override fun onCreate() { super.onCreate(); CallManager.service = this; ensureChannel(this) }
    override fun onDestroy() { if (CallManager.service === this) CallManager.service = null; super.onDestroy() }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(this, call)
        val snapshot = CallManager.current.value ?: return
        notify(snapshot)
        // Foreground launches are allowed for the default dialer; the full-screen notification covers the background case.
        runCatching { startActivity(InCallActivity.intent(this)) }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) { super.onCallAudioStateChanged(audioState); CallManager.onAudioStateChanged(audioState) }

    private fun notify(call: ActiveCall) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(this, 0, InCallActivity.intent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val contactName = Calls.matchContact(call.number, runCatching { (applicationContext as app.persora.android.PersoraApp).container.vault.contacts.value }.getOrDefault(emptyList()))?.name
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(contactName ?: call.number.ifBlank { "Unknown number" }).setContentText(if (call.isRinging) "Incoming call · Persora" else "Call in progress · Persora")
            .setCategory(if (call.isRinging) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_SERVICE).setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true).setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (call.isRinging) {
            builder.setFullScreenIntent(open, true)
            builder.addAction(0, "Decline", action(ACTION_DECLINE)).addAction(0, "Answer", action(ACTION_ANSWER))
        } else builder.addAction(0, "Hang up", action(ACTION_HANGUP))
        val n = builder.build()
        if (call.isRinging) n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(NOTIFICATION_ID, n)
    }

    private fun action(action: String): PendingIntent = PendingIntent.getBroadcast(this, action.hashCode(), Intent(this, CallActionReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val CHANNEL = "persora_calls"
        const val NOTIFICATION_ID = 7001
        const val ACTION_ANSWER = "app.persora.android.CALL_ANSWER"
        const val ACTION_DECLINE = "app.persora.android.CALL_DECLINE"
        const val ACTION_HANGUP = "app.persora.android.CALL_HANGUP"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Phone calls", NotificationManager.IMPORTANCE_HIGH).apply { description = "Incoming and ongoing calls handled by Persora"; setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
        }
    }
}

/** Notification buttons for the call notification. */
class CallActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PersoraInCallService.ACTION_ANSWER -> { CallManager.answer(); runCatching { context.startActivity(InCallActivity.intent(context)) } }
            PersoraInCallService.ACTION_DECLINE -> CallManager.reject()
            PersoraInCallService.ACTION_HANGUP -> CallManager.hangup()
        }
    }
}
