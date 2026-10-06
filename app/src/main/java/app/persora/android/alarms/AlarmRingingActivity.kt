package app.persora.android.alarms

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.PersoraTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen "Alarm is ringing" surface (ScheduleAlertOverlay on the web), shown over the lock screen. */
class AlarmRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val entry = AlarmScheduler(this).decode(intent.getStringExtra(ScheduleReceiver.EXTRA_ENTRY))
        setContent { PersoraTheme { RingingScreen(entry?.title ?: "Persora alarm", entry?.ringtoneName.orEmpty(), onSnooze = { finishWith(ScheduleReceiver.ACTION_SNOOZE) }, onDismiss = { finishWith(ScheduleReceiver.ACTION_DISMISS) }) } }
    }

    private fun finishWith(action: String) {
        val entryRaw = intent.getStringExtra(ScheduleReceiver.EXTRA_ENTRY)
        sendBroadcast(Intent(this, ScheduleReceiver::class.java).apply { this.action = action; putExtra(ScheduleReceiver.EXTRA_ENTRY, entryRaw) })
        AlarmSoundService.stop(this)
        AlarmScheduler(this).decode(entryRaw)?.let { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(it.id.hashCode()) }
        finish()
    }

    companion object {
        fun intent(context: Context, entry: AlarmScheduler.Scheduled): Intent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(ScheduleReceiver.EXTRA_ENTRY, kotlinx.serialization.json.Json.encodeToString(AlarmScheduler.Scheduled.serializer(), entry))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
    }
}

@Composable
private fun RingingScreen(title: String, ringtone: String, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bento.bg, Bento.bg))).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(104.dp).clip(CircleShape).background(Accents.violet.soft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Alarm, contentDescription = null, tint = Accents.violet.text, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm")), fontSize = 64.sp, fontWeight = FontWeight.Light, color = Bento.fg, letterSpacing = (-2).sp)
        Text("ALARM IS RINGING", style = MaterialTheme.typography.labelSmall, color = Accents.violet.text)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Bento.fg, textAlign = TextAlign.Center)
        if (ringtone.isNotBlank()) Text(ringtone, style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
        Spacer(Modifier.height(44.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(0.7f).height(54.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Bento.primary)) { Text("Dismiss", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth(0.7f).height(50.dp), shape = CircleShape) { Text("Snooze 10 minutes") }
    }
}
