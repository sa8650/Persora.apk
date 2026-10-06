package app.persora.android.ui.calls

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.persora.android.calls.Calls
import app.persora.android.calls.SimOption
import app.persora.android.ui.components.ToneIconBox
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones

/**
 * Returns a `call(number, displayName)` action that behaves like the stock phone app: asks for the phone permission
 * once, shows a "Choose SIM" sheet on dual-SIM phones (unless a default SIM is set in system settings), then places
 * the call directly through Telecom — no external dialer screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCaller(): (String, String?) -> Unit {
    val context = LocalContext.current
    val notify = LocalNotify.current
    var pending by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var simChoices by remember { mutableStateOf<List<SimOption>?>(null) }

    fun proceed(number: String, name: String?) {
        try {
            val sims = Calls.simOptions(context)
            val default = Calls.defaultSim(context)
            when {
                sims.size > 1 && default == null -> { pending = number to name; simChoices = sims }
                else -> Calls.place(context, number, default ?: sims.firstOrNull()?.handle, name)
            }
        } catch (e: SecurityException) { notify("Allow Persora to make phone calls in system settings.", true) } catch (e: Exception) { notify(e.message ?: "Couldn't start the call.", true) }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val p = pending
        if (grants[Manifest.permission.CALL_PHONE] == true && p != null) proceed(p.first, p.second) else notify("Phone permission is needed to call from Persora.", true)
    }

    simChoices?.let { sims ->
        ModalBottomSheet(onDismissRequest = { simChoices = null }, containerColor = Bento.card, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Call with", style = MaterialTheme.typography.titleLarge, color = Bento.fg)
                Text(pending?.second ?: pending?.first.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
                Spacer(Modifier.height(12.dp))
                sims.forEachIndexed { index, sim ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { val p = pending; simChoices = null; if (p != null) runCatching { Calls.place(context, p.first, sim.handle, p.second) }.onFailure { notify(it.message ?: "Couldn't start the call.", true) } }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (sim.color != 0) Color(sim.color) else Bento.primary), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SimCard, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(sim.label, style = MaterialTheme.typography.titleSmall, color = Bento.fg); Text(sim.description.ifBlank { "SIM ${index + 1}" }, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
                        Icon(Icons.Outlined.Call, null, tint = Accents.emerald.text)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Set a default SIM in Android Settings → Network & internet → SIMs to skip this step.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
            }
        }
    }

    return remember(context) {
        { number: String, name: String? ->
            val clean = Calls.normalize(number)
            if (clean.isBlank()) notify("That contact has no phone number.", true)
            else if (Calls.hasCallPermission(context)) proceed(clean, name)
            else { pending = clean to name; permission.launch(Calls.CALL_PERMISSIONS) }
        }
    }
}

/** Card nudging the member to make Persora the phone app so calls use Persora's own in-call screen. */
@Composable
fun DefaultDialerCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    var isDefault by remember { mutableStateOf(Calls.isDefaultDialer(context)) }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = Calls.isDefaultDialer(context); if (isDefault) notify("Persora is now your phone app.", false) }
    if (isDefault || !Calls.supportsDialerRole(context)) return
    app.persora.android.ui.components.BentoCard(modifier = modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIconBox(Icons.Outlined.PhoneInTalk, Tones.Blue, size = 38.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Use Persora's in-call screen", style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                Text("Make Persora your phone app so calls open here instead of the system dialer.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
            }
            Spacer(Modifier.width(8.dp))
            app.persora.android.ui.components.SoftButton("Set up", onClick = { runCatching { roleRequest.launch(Calls.requestDefaultDialerIntent(context)) }.onFailure { notify("Couldn't open the phone-app picker.", true) } })
        }
    }
}
