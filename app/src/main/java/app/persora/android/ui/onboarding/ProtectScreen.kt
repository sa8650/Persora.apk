package app.persora.android.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.persora.android.ui.components.Eyebrow
import app.persora.android.ui.components.BentoCard
import app.persora.android.ui.components.PrimaryButton
import app.persora.android.ui.components.ToneIconBox
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tone
import app.persora.android.ui.theme.Tones

/**
 * Permission priming after account creation (Dropbox/1Password pattern: explain *why* before the OS dialog).
 * Each row is optional and explains the trade-off; nothing blocks entry to the vault.
 */
@Composable
fun ProtectScreen(appLockEnabled: Boolean, onToggleAppLock: (Boolean) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var notifGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    var exactAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) }
    val biometricAvailable = remember { BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS }

    Column(Modifier.fillMaxSize().background(Bento.bg).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(12.dp))
        Eyebrow("Almost there")
        Spacer(Modifier.height(6.dp))
        Text("Set up your protections.", style = MaterialTheme.typography.headlineMedium, color = Bento.fg)
        Spacer(Modifier.height(6.dp))
        Text("These make reminders reliable and keep your vault private on this device. You can change each one later in Settings.", style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
        Spacer(Modifier.height(22.dp))

        ProtectRow(Icons.Outlined.NotificationsActive, Tones.Blue, "Allow notifications", "Reminders, alarms and sharing activity reach you even when Persora is closed—something the website can't do.", done = notifGranted, actionLabel = "Allow") {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else notifGranted = true
        }
        Spacer(Modifier.height(10.dp))
        ProtectRow(Icons.Outlined.Alarm, Tones.Purple, "Exact alarms", "Lets alarms ring at the precise minute you set, including in Doze mode.", done = exactAllowed, actionLabel = "Open settings") {
            if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}")))
        }
        Spacer(Modifier.height(10.dp))
        BentoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIconBox(Icons.Outlined.Fingerprint, Tones.Green, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("App lock", style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                    Text(if (biometricAvailable) "Require your fingerprint, face or device PIN whenever Persora opens." else "No screen lock is set on this device. Set one in Android settings to enable app lock.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                }
                Switch(checked = appLockEnabled, onCheckedChange = onToggleAppLock, enabled = biometricAvailable, colors = SwitchDefaults.colors(checkedTrackColor = Bento.primary))
            }
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Open my vault", onClick = onDone, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip for now", color = Bento.mutedFg) }
    }
    // Re-check exact alarm permission when returning from settings.
    androidx.compose.runtime.DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) exactAllowed = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms() }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
}

@Composable
private fun ProtectRow(icon: ImageVector, tone: Tone, title: String, body: String, done: Boolean, actionLabel: String, onAction: () -> Unit) {
    BentoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIconBox(icon, tone, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
            }
            Spacer(Modifier.width(8.dp))
            if (done) Icon(Icons.Outlined.CheckCircle, "Enabled", tint = Accents.emerald.text, modifier = Modifier.size(24.dp))
            else TextButton(onClick = onAction) { Text(actionLabel, color = Bento.primary) }
        }
    }
}
