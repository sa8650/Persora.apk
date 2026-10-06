package app.persora.android.ui.calls

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.calls.CallEntry
import app.persora.android.calls.CallLogStore
import app.persora.android.calls.Calls
import app.persora.android.calls.Keypad
import app.persora.android.ui.components.*
import app.persora.android.ui.contacts.ContactPhoto
import app.persora.android.ui.navigation.Details
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "Recents" like every phone app: system call log (when permitted) merged with Persora's own calls, plus a dialpad. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(initialDial: String? = null) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val vault = context.appContainer.vault
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    val own by CallLogStore.entries.collectAsStateWithLifecycle()
    val call = rememberCaller()
    var systemLog by remember { mutableStateOf<List<CallEntry>?>(null) }
    var hasLogPermission by remember { mutableStateOf(Calls.hasCallLogPermission(context)) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var dial by rememberSaveable { mutableStateOf(initialDial != null) }
    var dialNumber by rememberSaveable { mutableStateOf(initialDial.orEmpty()) }
    var refreshTick by remember { mutableStateOf(0) }

    val logPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasLogPermission = granted; refreshTick++ }
    LaunchedEffect(refreshTick, hasLogPermission) { CallLogStore.load(context); systemLog = CallLogStore.systemLog(context) }
    // Refresh when a call we just placed/handled finishes (own log updates immediately; system log slightly later).
    LaunchedEffect(own) { systemLog = CallLogStore.systemLog(context) }

    val merged = remember(own, systemLog, contacts, filter) {
        val sys = systemLog.orEmpty()
        val list = if (sys.isNotEmpty()) sys + own.filter { o -> sys.none { s -> kotlin.math.abs(s.startedAt - o.startedAt) < 60_000 && s.number.takeLast(7) == o.number.takeLast(7) } } else own
        list.map { e -> if (e.name == null) e.copy(name = Calls.matchContact(e.number, contacts)?.name) else e }
            .sortedByDescending { it.startedAt }
            .filter { filter == 0 || it.direction == "missed" || it.direction == "rejected" }
    }
    val grouped = remember(merged) { merged.groupBy { dayLabel(it.startedAt) } }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, floatingActionButton = {
        FloatingActionButton(onClick = { dial = true }, containerColor = Bento.primary, contentColor = Bento.primaryFg, shape = CircleShape) { Icon(Icons.Outlined.Dialpad, "Dialpad") }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { DefaultDialerCard() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SegmentedTabs(listOf("All", "Missed"), filter, { filter = it }, Modifier.weight(1f))
                    IconButton(onClick = { refreshTick++ }) { Icon(Icons.Outlined.Refresh, "Refresh", tint = Bento.mutedFg) }
                }
            }
            if (!hasLogPermission) item {
                BentoCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIconBox(Icons.Outlined.History, Tones.Amber, size = 38.dp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text("Show your full call history", style = MaterialTheme.typography.titleSmall, color = Bento.fg); Text("Allow call-log access to see calls made with any app, not only Persora.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
                        Spacer(Modifier.width(8.dp)); SoftButton("Allow", onClick = { logPermission.launch(Manifest.permission.READ_CALL_LOG) })
                    }
                }
            }
            if (merged.isEmpty()) item { EmptyState(if (filter == 1) "No missed calls." else "No calls yet.", "Call someone from their contact card or use the dialpad — calls you make or receive show up here.", Icons.Outlined.Call) }
            grouped.forEach { (day, list) ->
                item { Text(day, style = MaterialTheme.typography.labelLarge, color = Bento.subtleFg, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) }
                items(list, key = { it.id }) { e ->
                    val contact = remember(e.number, contacts) { Calls.matchContact(e.number, contacts) }
                    BentoCard(padding = 12.dp, onClick = { call(e.number, e.name) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (contact != null) ContactPhoto(contact, 42.dp) else Avatar((e.name ?: "#").take(2).uppercase(), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name ?: e.number.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleSmall, color = if (e.direction == "missed") Bento.danger else Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (icon, tint) = when (e.direction) { "outgoing" -> Icons.AutoMirrored.Outlined.CallMade to Accents.emerald.text; "incoming" -> Icons.AutoMirrored.Outlined.CallReceived to Bento.primary; else -> Icons.AutoMirrored.Outlined.CallMissed to Bento.danger }
                                    Icon(icon, e.direction, tint = tint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                                    Text(listOfNotNull(if (e.name != null) e.number else null, e.sim, if (e.durationSec > 0) Calls.formatDuration(e.durationSec) else null).joinToString(" · ").ifBlank { e.direction.replaceFirstChar { it.uppercase() } }, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(timeLabel(e.startedAt), style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg)
                                if (contact != null) TextButton(onClick = { Details.openContact(contact.id) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) { Text("Open", style = MaterialTheme.typography.labelMedium, color = Bento.primary) }
                            }
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { call(e.number, e.name) }) { Icon(Icons.Outlined.Call, "Call back", tint = Accents.emerald.text) }
                        }
                    }
                }
            }
            if (own.isNotEmpty()) item { TextButton(onClick = { CallLogStore.clear(context) }) { Text("Clear Persora call history", color = Bento.mutedFg) } }
        }
    }

    if (dial) {
        ModalBottomSheet(onDismissRequest = { dial = false }, containerColor = Bento.card, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            val match = remember(dialNumber, contacts) { Calls.matchContact(dialNumber, contacts) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(48.dp))
                    Text(dialNumber.ifBlank { " " }, modifier = Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Bento.fg, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                    IconButton(onClick = { dialNumber = dialNumber.dropLast(1) }, enabled = dialNumber.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = Bento.mutedFg) }
                }
                Text(match?.name ?: " ", style = MaterialTheme.typography.bodyMedium, color = Bento.primary)
                Spacer(Modifier.height(8.dp))
                Keypad(onDigit = { d -> if (dialNumber.length < 20) dialNumber += d }, light = true)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(70.dp).clip(CircleShape).background(if (dialNumber.isBlank()) Bento.muted else Accents.emerald.c500).clickable(enabled = dialNumber.isNotBlank()) { call(dialNumber, match?.name); dial = false }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Call, "Call", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

private fun dayLabel(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> date.format(DateTimeFormatter.ofPattern("EEEE"))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

private fun timeLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
