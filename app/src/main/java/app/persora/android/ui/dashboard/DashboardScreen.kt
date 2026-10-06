package app.persora.android.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.storage.SecurePrefs
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import app.persora.android.data.model.AppUser
import app.persora.android.data.model.Sections
import app.persora.android.data.model.VaultItem
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.Details
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Accent
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.MonoBody
import app.persora.android.ui.theme.MonoCaption
import app.persora.android.ui.theme.MonoStat
import app.persora.android.ui.theme.Tones
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/* ─────────────────────────────────────────────────────────────────────────────
   Home — the Agent Bento Grid, fed by the member's real vault:
     · Weekly activity  (Card2  – hatched bar chart + stat tiles)
     · Coming up        (Card3  – activity feed with status tiles)
     · Spaces           (Card5  – 3-D icon tiles with counts + share bars)
     · Storage          (Card4  – namespace bars + live dot)
     · Sync pipeline    (Card1  – node graph with flowing packets)
     · Recent / Favorites
   ───────────────────────────────────────────────────────────────────────────── */

private data class Greeting(val headline: String, val line: String)

private fun greetingFor(name: String, timezone: String, now: ZonedDateTime = ZonedDateTime.now(runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault()))): Greeting {
    val first = name.trim().substringBefore(' ').ifBlank { "there" }
    val hour = now.hour
    val (head, lines) = when (hour) {
        in 5..11 -> "Good morning, $first" to listOf("A fresh page. What would you like to keep safe today?", "Coffee first, then the important things.", "Everything important, gathered in one place.")
        in 12..16 -> "Good afternoon, $first" to listOf("Halfway through the day — your vault is up to date.", "A good moment to tidy a folder or tick off a task.", "Everything important, gathered in one place.")
        in 17..20 -> "Good evening, $first" to listOf("Wind down. Tomorrow's dates are already on your radar.", "A quiet check-in before the day closes.", "Everything important, gathered in one place.")
        else -> (if (hour < 5) "Up late, $first" else "Good night, $first") to listOf("Reminders will ring even when you're not here.", "Set it down here — it will be waiting in the morning.", "Everything important, gathered in one place.")
    }
    return Greeting(head, lines[(now.dayOfYear + now.hour / 6) % lines.size])
}

@Composable
fun DashboardScreen(user: AppUser) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val nav = LocalNav.current
    val items by vault.items.collectAsStateWithLifecycle()
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    val medical by vault.medicalRecords.collectAsStateWithLifecycle()
    val offline by vault.offline.collectAsStateWithLifecycle()
    val lastSynced by vault.lastSyncedAt.collectAsStateWithLifecycle()
    var checklistDismissed by remember { mutableStateOf(container.prefs.getBoolean(SecurePrefs.KEY_CHECKLIST_DISMISSED)) }
    var greeting by remember(user.fullName, user.timezone) { mutableStateOf(greetingFor(user.fullName, user.timezone)) }
    LaunchedEffect(user.fullName, user.timezone) { while (true) { kotlinx.coroutines.delay(60_000); greeting = greetingFor(user.fullName, user.timezone) } }

    val upcoming = remember(items) {
        val now = ZonedDateTime.now(); val horizon = now.plusDays(45)
        items.filter { it.sharedAccess == null }.mapNotNull { item ->
            if (item.isTodo && item.metadata["completed"] == "true") return@mapNotNull null
            if (item.isSchedule && item.metadata["enabled"] == "false") return@mapNotNull null
            val at = Dates.dashboardDate(item, Sections[item.section].dateKey) ?: return@mapNotNull null
            if (at.isBefore(now.minusDays(1)) || at.isAfter(horizon)) null else item to at
        }.sortedBy { it.second }.take(5)
    }
    val recent = remember(items) { items.sortedByDescending { it.updatedAt }.take(5) }
    val favorites = remember(items) { items.filter { it.favorite }.sortedByDescending { it.updatedAt }.take(5) }
    val week = remember(items) { weeklyActivity(items) }
    val isWide = androidx.compose.material3.adaptive.currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != androidx.window.core.layout.WindowWidthSizeClass.COMPACT

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isWide) 2 else 1), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting.headline, style = MaterialTheme.typography.headlineSmall, color = Bento.fg)
                    Text(greeting.line, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                }
                PrimaryButton("Add", onClick = { nav.navigate(Routes.editor("documents")) }, icon = Icons.Outlined.Add)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { KpiRow(items, contacts.size, upcoming.size) }

        if (!checklistDismissed) item(span = { GridItemSpan(maxLineSpan) }) {
            GettingStartedCard(items = items, contactsCount = contacts.size, appLock = container.session.appLockEnabled, onDismiss = { container.prefs.putBoolean(SecurePrefs.KEY_CHECKLIST_DISMISSED, true); checklistDismissed = true })
        }

        item { ActivityCard(week, items.size, contacts.size, items.count { it.file != null }, Modifier.riseIn(0)) }
        item { ComingUpCard(upcoming, Modifier.riseIn(60)) }
        item { ActivityFeedCard(recent, Modifier.riseIn(120)) }
        item(span = { GridItemSpan(maxLineSpan) }) { SyncPipelineCard(offline, lastSynced, items.size + contacts.size + medical.size, Modifier.riseIn(240)) }

        if (favorites.isNotEmpty()) item {
            BentoCard(Modifier.riseIn(340)) {
                SectionHeading("Favorites", "Pinned records", favorites.size)
                Spacer(Modifier.height(6.dp))
                favorites.forEachIndexed { i, item -> if (i > 0) HairLine(); RecentRow(item) { Details.openItem(item.id) } }
            }
        }
    }
}

/* ───────────────────────── Card 2 · Weekly activity ───────────────────────── */

private data class DayStat(val label: String, val count: Int, val isToday: Boolean)

private fun weeklyActivity(items: List<VaultItem>): List<DayStat> {
    val today = LocalDate.now()
    return (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        val n = items.count { Dates.parseInstant(it.updatedAt)?.toLocalDate() == day }
        DayStat(day.format(DateTimeFormatter.ofPattern("EEE")).uppercase().take(3), n, back == 0)
    }
}

@Composable
private fun ActivityCard(week: List<DayStat>, records: Int, people: Int, files: Int, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val thisWeek = week.sumOf { it.count }
    FeatCard("Activity", "Records you added or updated over the last seven days.", modifier, onClick = { nav.navigate(Routes.SPACES) }, panelPadding = 12.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatTile("Records", records, "+$thisWeek", "7d", Modifier.weight(1f))
                StatTile("People", people, null, null, Modifier.weight(1f))
                StatTile("Files", files, null, null, Modifier.weight(1f))
            }
            val max = (week.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, d -> HatchBar(d.count / max.toFloat(), i, d.isToday, Modifier.weight(1f).fillMaxHeight()) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.forEach { d -> Text(d.label, style = MonoCaption.copy(letterSpacing = 0.6.sp), color = if (d.isToday) Bento.fg else Bento.mutedFg, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, trend: String?, trendNote: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        MonoLabel(label)
        Text(value.toString().padStart(2, '0'), style = MonoStat, color = Bento.fg, modifier = Modifier.padding(top = 4.dp))
        if (trend != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(trend, style = MonoCaption.copy(letterSpacing = 0.4.sp, fontWeight = FontWeight.Bold), color = if (trend.startsWith("+") && trend != "+0") Accents.emerald.c500 else Bento.subtleFg)
            if (trendNote != null) Text(trendNote, style = MonoCaption.copy(letterSpacing = 0.4.sp), color = Bento.subtleFg)
        }
    }
}

/** One chart column: hatched track with a primary-coloured fill that breathes gently (the Card2 bars). */
@Composable
private fun HatchBar(fraction: Float, index: Int, highlight: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val target by animateFloatAsState(fraction.coerceIn(0.06f, 1f), tween(700, delayMillis = index * 70), label = "bar")
    val t = rememberInfiniteTransition(label = "breathe")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000 + (index % 3) * 800, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val h = (target * (0.94f + 0.06f * breathe)).coerceIn(0.06f, 1f)
    Box(modifier.clip(shape).hatch().border(1.dp, Bento.borderStrong.copy(alpha = 0.7f), shape), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(h).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(if (highlight) Bento.primary else Bento.primary.copy(alpha = 0.82f))
                .drawBehind { drawLine(Color.White.copy(alpha = 0.6f), Offset(4f, 1f), Offset(size.width - 4f, 1f), strokeWidth = 1f); drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.08f)))) }
        )
    }
}

/* ───────────────────────── Card 3 · Coming up (activity feed) ───────────────────────── */

@Composable
private fun ComingUpCard(upcoming: List<Pair<VaultItem, ZonedDateTime>>, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    FeatCard("Coming up", "Due tasks, reminders, alarms and expiring records, soonest first.", modifier, onClick = { nav.navigate(Routes.section("notes")) }, panelPadding = 8.dp) {
        if (upcoming.isEmpty()) Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Check, Accents.emerald, size = 30.dp)
            Spacer(Modifier.width(10.dp)); Column { Text("A clear horizon", style = MaterialTheme.typography.titleSmall, color = Bento.fg); Text("No dates need your attention just yet.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
        } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            upcoming.forEachIndexed { i, (item, at) -> FeedRow(item, at, active = i == 0) { Details.openItem(item.id) } }
        }
    }
}

@Composable
private fun FeedRow(item: VaultItem, at: ZonedDateTime, active: Boolean, onClick: () -> Unit) {
    val section = Sections[item.section]
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), at.toLocalDate())
    val (status, accent, icon) = when {
        days < 0 -> Triple("overdue", Accents.rose, Icons.Outlined.PriorityHigh)
        days <= 2 -> Triple("due", Accents.amber, if (item.isAlarm) Icons.Outlined.Alarm else if (item.isReminder) Icons.Outlined.NotificationsActive else Icons.Outlined.Schedule)
        else -> Triple("queued", Accents.emerald, if (item.isTodo) Icons.Outlined.CheckCircle else section.icon)
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (active) Bento.card else Color.Transparent).border(1.dp, if (active) Bento.ring else Bento.border.copy(alpha = 0.6f), shape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, accent, size = if (active) 30.dp else 24.dp, radius = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MonoBody.copy(fontWeight = FontWeight.SemiBold, fontSize = if (active) 11.sp else 10.sp), color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Pill(status, accent.toTone())
            }
            Text("${when { item.isTodo -> "Task"; item.isReminder -> "Reminder"; item.isAlarm -> "Alarm"; else -> section.label }} · ${if (days < 0) "${-days}d ago" else if (days == 0L) "today" else "in ${days}d"}", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(if (item.isSchedule) Dates.formatTime(at) else at.format(DateTimeFormatter.ofPattern("MMM d")), style = MonoCaption.copy(letterSpacing = 0.3.sp), color = Bento.mutedFg)
    }
}

private fun Accent.toTone() = app.persora.android.ui.theme.Tone(text, soft, line, this)

/* ───────────────────────── Card 5 · Spaces (tool inspector) ───────────────────────── */

@Composable
private fun SpacesCard(items: List<VaultItem>, contacts: Int, medical: Int, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    data class Space(val label: String, val icon: ImageVector, val accent: Accent, val count: Int, val route: String)
    val spaces = remember(items, contacts, medical) {
        val all = Sections.all.map { Space(it.label, it.icon, Accents.byName(it.color), items.count { i -> i.section == it.id }, Routes.section(it.id)) } +
            Space("Contacts", Icons.Outlined.ContactPage, Accents.sky, contacts, Routes.CONTACTS) + Space("Medical", Icons.Outlined.MonitorHeart, Accents.rose, medical, Routes.MEDICAL)
        all.sortedByDescending { it.count }.take(4)
    }
    val total = spaces.sumOf { it.count }.coerceAtLeast(1)
    FeatCard("Spaces", "Where your records live. Tap a tile to open that space.", modifier, onClick = { nav.navigate(Routes.SPACES) }, panelPadding = 8.dp,
        trailing = { TextButton(onClick = { nav.navigate(Routes.SPACES) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("ALL", style = MonoCaption, color = Bento.mutedFg) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            spaces.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s -> ToolTile(s.label, s.icon, s.accent, s.count, s.count / total.toFloat(), Modifier.weight(1f)) { nav.navigate(s.route) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Card5 tool tile: 3-D icon + count top row, name + share bar bottom row. */
@Composable
fun ToolTile(name: String, icon: ImageVector, accent: Accent, count: Int, fraction: Float, modifier: Modifier = Modifier, unit: String = "items", iconSize: Dp = 34.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(Bento.card).border(1.dp, Bento.ring, shape).clickable(onClick = onClick).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(icon, accent, size = iconSize, radius = iconSize * 0.3f)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(count.toString(), style = MonoBody.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Bento.fg)
                Text(unit.uppercase(), style = MonoCaption.copy(fontSize = 7.sp), color = Bento.subtleFg)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MonoBody.copy(fontSize = 10.sp), color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${(fraction * 100).toInt()}%", style = MonoCaption.copy(letterSpacing = 0.3.sp), color = Bento.mutedFg)
            }
            ProgressTrack(fraction, accent, height = 5.dp)
        }
    }
}

/* ───────────────────────── Card 4 · Storage (knowledge base) ───────────────────────── */

@Composable
fun StorageCard(items: List<VaultItem>, storage: app.persora.android.data.model.StorageUsage?, offline: Boolean, lastSynced: Long, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    data class Ns(val label: String, val icon: ImageVector, val accent: Accent, val bytes: Long, val files: Int)
    val namespaces = remember(items) {
        val bySection = items.filter { it.file != null }.groupBy { it.section }
        bySection.map { (id, list) -> val s = Sections[id]; Ns(s.label, s.icon, Accents.byName(s.color), list.sumOf { it.file?.size ?: 0L }, list.size) }.sortedByDescending { it.bytes }.take(4)
    }
    val used = storage?.bytesUsed ?: namespaces.sumOf { it.bytes }
    val limit = storage?.storageLimitBytes?.takeIf { it > 0 } ?: 0L
    val maxNs = (namespaces.maxOfOrNull { it.bytes } ?: 0L).coerceAtLeast(1L)
    FeatCard("Storage", "Files in your private R2 vault, grouped by space.", modifier, onClick = { nav.navigate(Routes.BILLING) }, panelPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoLabel("Namespaces")
            if (namespaces.isEmpty()) Text("No files yet — attach one to any record.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
            namespaces.forEach { ns ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconTile(ns.icon, ns.accent, size = 30.dp, radius = 10.dp)
                    Text(ns.label, style = MonoBody.copy(fontSize = 10.sp), color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(72.dp))
                    ProgressTrack(ns.bytes / maxNs.toFloat(), ns.accent, Modifier.weight(1f), height = 6.dp, shimmer = ns === namespaces.first())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(Files.formatSize(ns.bytes), style = MonoCaption.copy(letterSpacing = 0.2.sp), color = Bento.mutedFg)
                        Box(Modifier.size(4.dp).clip(CircleShape).background(ns.accent.c500))
                    }
                }
            }
            HairLine()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${Files.formatSize(used)}${if (limit > 0) " of ${Files.formatSize(limit)}" else ""}", style = MonoBody.copy(fontWeight = FontWeight.Bold), color = Bento.fg)
                    if (storage != null) Text(storage.planName.uppercase(), style = MonoCaption, color = Bento.subtleFg)
                }
                if (offline) Pill("offline", Tones.Amber, Icons.Outlined.CloudOff) else LiveDot(label = if (lastSynced > 0) "synced ${Dates.formatRelative(java.time.Instant.ofEpochMilli(lastSynced).toString())}" else "live sync")
            }
            if (limit > 0) ProgressTrack(used / limit.toFloat(), if (used / limit.toFloat() > 0.9f) Accents.rose else Accents.zinc, height = 4.dp)
        }
    }
}

/* ───────────────────────── Card 1 · Sync pipeline (node graph) ───────────────────────── */

private data class Node(val x: Float, val y: Float, val icon: ImageVector?, val label: String, val accent: Accent)

@Composable
private fun SyncPipelineCard(offline: Boolean, lastSynced: Long, records: Int, modifier: Modifier = Modifier) {
    FeatCard("Sync pipeline", "How this phone, the Persora API and your encrypted storage stay in step — quietly, in the background.", modifier, panelPadding = 0.dp, panelHeight = 176.dp) {
        PipelineGraph(offline, records, Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (offline) Pill("cached", Tones.Amber, Icons.Outlined.CloudOff) else LiveDot(label = "background sync")
        }
        Text("$records RECORDS", style = MonoCaption, color = Bento.subtleFg, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
    }
}

@Composable
private fun PipelineGraph(offline: Boolean, records: Int, modifier: Modifier = Modifier) {
    val nodes = listOf(
        Node(48f, 88f, Icons.Outlined.PhoneAndroid, "PHONE", Accents.cyan),
        Node(124f, 88f, null, "", Accents.amber),
        Node(200f, 88f, Icons.Outlined.Dns, "API", Accents.violet),
        Node(284f, 40f, Icons.Outlined.Storage, "DB", Accents.fuchsia),
        Node(284f, 136f, Icons.Outlined.Cloud, "R2", Accents.emerald),
    )
    val t = rememberInfiniteTransition(label = "flow")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")
    val step by t.animateFloat(0f, 6f, infiniteRepeatable(tween(7200, easing = LinearEasing)), label = "step")
    val active = step.toInt() % 6 // request · router · agent · memory · tools · response
    BoxWithConstraints(modifier.dotGrid()) {
        val sx = maxWidth.value / 320f; val sy = maxHeight.value / 176f
        val s = minOf(sx, sy)
        val ox = (maxWidth.value - 320f * s) / 2f; val oy = (maxHeight.value - 176f * s) / 2f
        fun px(x: Float) = (ox + x * s).dp
        fun py(y: Float) = (oy + y * s).dp
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            fun p(x: Float, y: Float) = Offset((ox + x * s) * d, (oy + y * s) * d)
            val base = Bento.borderStrong.copy(alpha = 0.9f)
            val segs = listOf(
                listOf(p(76f, 88f), p(112f, 88f)) to (if (active == 0 || active == 5) Accents.cyan else null),
                listOf(p(136f, 88f), p(172f, 88f)) to (if (active == 2 || active == 5) Accents.violet else null),
                listOf(p(200f, 62f), p(200f, 40f), p(256f, 40f)) to (if (active == 3) Accents.fuchsia else null),
                listOf(p(200f, 114f), p(200f, 136f), p(256f, 136f)) to (if (active == 4) Accents.emerald else null),
            )
            segs.forEach { (pts, acc) ->
                for (i in 0 until pts.size - 1) drawLine(base, pts[i], pts[i + 1], strokeWidth = 1.2f * d)
                if (acc != null && !offline) {
                    val col = acc.c500
                    for (i in 0 until pts.size - 1) drawLine(col.copy(alpha = 0.45f), pts[i], pts[i + 1], strokeWidth = 1.6f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * d, 6f * d), -phase * 24f * d))
                    // travelling packet
                    val total = (0 until pts.size - 1).sumOf { (pts[it + 1] - pts[it]).getDistance().toDouble() }.toFloat()
                    var dist = phase * total; var pos = pts.first()
                    for (i in 0 until pts.size - 1) { val seg = pts[i + 1] - pts[i]; val len = seg.getDistance(); if (dist <= len) { pos = pts[i] + seg * (dist / len); break } else { dist -= len; pos = pts[i + 1] } }
                    drawCircle(col, radius = 3.2f * d, center = pos)
                    drawCircle(col.copy(alpha = 0.25f), radius = 7f * d, center = pos)
                }
            }
        }
        nodes.forEach { n ->
            if (n.icon != null) {
                Column(Modifier.offset(px(n.x) - 24.dp, py(n.y) - 24.dp).size(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(n.accent.tile).border(1.dp, n.accent.c600, RoundedCornerShape(14.dp)).drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent, Color.Black.copy(alpha = 0.1f)))) }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(n.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(n.label, style = MonoCaption.copy(fontSize = 7.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                }
            } else {
                // Router node: ring with a slowly rotating dashed core, amber when traffic passes.
                val routing = active == 1 || active == 5
                val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "rot")
                Box(Modifier.offset(px(n.x) - 10.dp, py(n.y) - 10.dp).size(20.dp).clip(CircleShape).background(if (routing && !offline) Accents.amber.soft else Bento.card).border(2.dp, if (routing && !offline) Accents.amber.c500.copy(alpha = 0.7f) else Bento.borderStrong, CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(10.dp)) { rotate(rot) { drawCircle(if (routing && !offline) Accents.amber.c500 else Bento.subtleFg, style = Stroke(width = 1.2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * density, 3f * density)), cap = StrokeCap.Round)) } }
                }
            }
        }
    }
}

/* ───────────────────────── KPI cards (Token Monitor stat tiles) ───────────────────────── */

private data class Kpi(val label: String, val value: Int, val delta: Int, val series: List<Int>, val accent: Accent, val route: String)

private fun dailyCounts(stamps: List<String>, days: Int = 7): List<Int> {
    val today = LocalDate.now()
    val dates = stamps.mapNotNull { Dates.parseInstant(it)?.toLocalDate() }
    return (days - 1 downTo 0).map { back -> val d = today.minusDays(back.toLong()); dates.count { it == d } }
}

@Composable
private fun KpiRow(items: List<VaultItem>, contacts: Int, due: Int) {
    val kpis = remember(items, contacts, due) {
        val created = items.map { it.createdAt }
        val lastWeek = created.count { Dates.parseInstant(it)?.isAfter(ZonedDateTime.now().minusDays(7)) == true }
        val files = items.filter { it.file != null }
        val notes = items.filter { it.section == "notes" }
        listOf(
            Kpi("Records", items.size, lastWeek, dailyCounts(items.map { it.updatedAt }), Accents.brand, Routes.SPACES),
            Kpi("Files", files.size, files.count { Dates.parseInstant(it.createdAt)?.isAfter(ZonedDateTime.now().minusDays(7)) == true }, dailyCounts(files.map { it.updatedAt }), Accents.violet, Routes.BILLING),
            Kpi("People", contacts, 0, List(7) { if (it == 6) contacts else contacts }, Accents.emerald, Routes.CONTACTS),
            Kpi("Due · 7d", due, notes.count { it.isTodo && it.metadata["completed"] != "true" }, dailyCounts(notes.map { it.updatedAt }), Accents.amber, Routes.section("notes")),
        )
    }
    val nav = LocalNav.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        kpis.chunked(2).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, k -> KpiCard(k, Modifier.weight(1f).riseIn((r * 2 + c) * 50)) { nav.navigate(k.route) } }
            }
        }
    }
}

/** One Token-Monitor stat card: mono label, counting value, trend vs prev, and a self-drawing sparkline. */
@Composable
private fun KpiCard(k: Kpi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val count by animateFloatAsState(if (shown) k.value.toFloat() else 0f, tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "count")
    val draw by animateFloatAsState(if (shown) 1f else 0f, tween(1100, delayMillis = 150, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "draw")
    val t = rememberInfiniteTransition(label = "kpi-pulse")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    BentoCard(modifier, onClick = onClick, padding = 12.dp, radius = 18.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                MonoLabel(k.label)
                Text(count.toInt().toString().padStart(2, '0'), style = MonoStat.copy(fontSize = 22.sp), color = Bento.fg, modifier = Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Text(if (k.delta > 0) "+${k.delta}" else if (k.delta < 0) "${k.delta}" else "±0", style = MonoCaption.copy(letterSpacing = 0.4.sp, fontWeight = FontWeight.Bold), color = if (k.delta > 0) Accents.emerald.c500 else if (k.delta < 0) Accents.rose.c400 else Bento.subtleFg)
                    Text("prev 7d", style = MonoCaption.copy(letterSpacing = 0.4.sp), color = Bento.subtleFg)
                }
            }
            // Sparkline (48×24 viewBox like Card2): faded baseline path, accent stroke drawn in, end dot pulsing.
            Canvas(Modifier.width(52.dp).height(26.dp)) {
                val series = k.series; val max = (series.maxOrNull() ?: 0).coerceAtLeast(1)
                val stepX = size.width / (series.size - 1).coerceAtLeast(1)
                val pts = series.mapIndexed { i, v -> Offset(i * stepX, size.height - (v / max.toFloat()) * (size.height - 4f) - 2f) }
                val path = androidx.compose.ui.graphics.Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                drawPath(path, Bento.borderStrong, style = Stroke(width = 1.2f * density, cap = StrokeCap.Round))
                val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
                val partial = androidx.compose.ui.graphics.Path()
                measure.getSegment(0f, measure.length * draw, partial, true)
                drawPath(partial, k.accent.c500, style = Stroke(width = 1.6f * density, cap = StrokeCap.Round))
                if (draw >= 0.999f) {
                    val end = pts.last()
                    drawCircle(k.accent.c500.copy(alpha = 0.25f * (1 - pulse) + 0.1f), radius = (3f + 3f * pulse) * density, center = end)
                    drawCircle(Bento.card, radius = 2.6f * density, center = end)
                    drawCircle(k.accent.c500, radius = 2.6f * density, center = end, style = Stroke(width = 1.4f * density))
                }
            }
        }
    }
}

/* ───────────────────────── Card 3 · Latest changes (animated activity feed) ───────────────────────── */

private data class FeedStatus(val label: String, val accent: Accent, val icon: ImageVector)

private fun feedStatus(item: VaultItem): FeedStatus = when {
    item.isTodo && item.metadata["completed"] == "true" -> FeedStatus("done", Accents.emerald, Icons.Outlined.Check)
    item.isTodo -> FeedStatus("running", Accents.sky, Icons.Outlined.Autorenew)
    item.isSchedule -> FeedStatus("pending", Accents.amber, Icons.Outlined.Schedule)
    item.createdAt.take(16) == item.updatedAt.take(16) -> FeedStatus("created", Accents.emerald, Icons.Outlined.Add)
    else -> FeedStatus("updated", Accents.violet, Icons.Outlined.Edit)
}

/**
 * The Agent-Bento "Activity Feed": entries ride a vertical carousel — the active row sits centred at full size,
 * neighbours shrink and fade above / below, the list advances every 2.2 s, with position dots underneath.
 */
@Composable
private fun ActivityFeedCard(recent: List<VaultItem>, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    FeatCard("Latest changes", "A live feed of the records you touched most recently.", modifier, onClick = { nav.navigate(Routes.SPACES) }, panelPadding = 0.dp, panelHeight = 196.dp) {
        if (recent.isEmpty()) {
            Row(Modifier.align(Alignment.Center).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Description, Accents.zinc, size = 30.dp); Spacer(Modifier.width(10.dp))
                Text("Your saved records will appear here.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
            }
            return@FeatCard
        }
        val n = recent.size
        var active by remember(n) { mutableStateOf(0) }
        LaunchedEffect(n) { while (true) { kotlinx.coroutines.delay(2200); active = (active + 1) % n } }
        Box(Modifier.fillMaxSize().dotGrid(), contentAlignment = Alignment.Center) {
            recent.forEachIndexed { i, item ->
                var slot = i - active
                if (slot > n / 2) slot -= n
                if (slot < -n / 2) slot += n
                val targetY = when (slot) { -2 -> -112f; -1 -> -58f; 0 -> 0f; 1 -> 58f; 2 -> 112f; else -> if (slot < 0) -150f else 150f }
                val visible = slot in -2..2
                val y by animateFloatAsState(targetY, tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "y")
                val scale by animateFloatAsState(if (slot == 0) 1f else if (kotlin.math.abs(slot) == 1) 0.9f else 0.82f, tween(420), label = "s")
                val alpha by animateFloatAsState(if (!visible) 0f else if (slot == 0) 1f else if (kotlin.math.abs(slot) == 1) 0.55f else 0.25f, tween(420), label = "a")
                if (alpha > 0.01f) FeedEntry(item, slot == 0, Modifier.offset(y = y.dp).scale(scale).alpha(alpha).padding(horizontal = 10.dp)) { Details.openItem(item.id) }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(n) { i -> val w by animateFloatAsState(if (i == active) 14f else 4f, tween(300), label = "dot"); Box(Modifier.height(3.dp).width(w.dp).clip(CircleShape).background(if (i == active) Bento.primary else Bento.fg.copy(alpha = 0.25f))) }
            }
        }
    }
}

@Composable
private fun FeedEntry(item: VaultItem, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val st = feedStatus(item)
    val section = Sections[item.section]
    val shape = RoundedCornerShape(16.dp)
    val spin = rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "deg")
    Row(
        modifier.fillMaxWidth().clip(shape).background(if (active) Bento.card else Bento.card.copy(alpha = 0.7f)).border(1.dp, if (active) Bento.ring else Bento.border, shape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = if (active) 10.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.rotate(if (st.label == "running") spin.value else 0f)) { IconTile(st.icon, st.accent, size = if (active) 30.dp else 22.dp, radius = 8.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MonoBody.copy(fontWeight = FontWeight.SemiBold, fontSize = if (active) 11.sp else 9.5.sp), color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Pill(st.label, st.accent.toTone())
            }
            Text("${section.label}${if (item.file != null) " · file attached" else ""}", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(Dates.formatRelative(item.updatedAt), style = MonoCaption.copy(letterSpacing = 0.2.sp), color = Bento.mutedFg)
    }
}

/* ───────────────────────── shared rows ───────────────────────── */

@Composable
fun RecentRow(item: VaultItem, onClick: () -> Unit) {
    val section = Sections[item.section]
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(section.icon, Accents.byName(section.color), size = 30.dp, radius = 9.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${section.label} · ${Dates.formatRelative(item.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1)
        }
        if (item.file != null) Icon(Icons.Outlined.AttachFile, null, tint = Bento.subtleFg, modifier = Modifier.size(14.dp))
        Icon(Icons.Outlined.ArrowOutward, null, tint = Bento.subtleFg, modifier = Modifier.size(14.dp))
    }
}

/** Dismissible first-run checklist (Dropbox "onboarding tasks" pattern). */
@Composable
private fun GettingStartedCard(items: List<VaultItem>, contactsCount: Int, appLock: Boolean, onDismiss: () -> Unit) {
    val nav = LocalNav.current
    data class Task(val label: String, val done: Boolean, val icon: ImageVector, val accent: Accent, val route: String)
    val tasks = listOf(
        Task("Save your first document", items.any { it.section == "documents" }, Icons.Outlined.Description, Accents.sky, Routes.editor("documents")),
        Task("Add a contact", contactsCount > 0, Icons.Outlined.ContactPage, Accents.violet, Routes.contactEditor()),
        Task("Set a reminder or alarm", items.any { it.isSchedule }, Icons.Outlined.NotificationsActive, Accents.amber, Routes.section("notes")),
        Task("Turn on app lock", appLock, Icons.Outlined.Fingerprint, Accents.emerald, Routes.SETTINGS),
    )
    val done = tasks.count { it.done }
    if (done == tasks.size) return
    BentoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { MonoLabel("Getting started"); Text("$done of ${tasks.size} complete", style = MaterialTheme.typography.titleSmall, color = Bento.fg) }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss", tint = Bento.subtleFg) }
        }
        ProgressTrack(done / tasks.size.toFloat(), Accents.brand, height = 5.dp)
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth(), padding = 6.dp) {
            Column {
                tasks.forEach { t ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = !t.done) { nav.navigate(t.route) }.padding(vertical = 7.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(t.icon, if (t.done) Accents.zinc else t.accent, size = 24.dp, radius = 7.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(t.label, style = MonoBody.copy(fontSize = 10.5.sp), color = if (t.done) Bento.subtleFg else Bento.fg, modifier = Modifier.weight(1f))
                        if (t.done) Pill("done", Tones.Green) else Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Bento.subtleFg, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/* ───────────────────────── Spaces (all sections) ───────────────────────── */

/** Spaces grid icons: 34dp base → +20% (v2.1) → +20% again (v2.4). */
private val SPACES_ICON = 41.dp

@Composable
fun SpacesScreen() {
    val vault = LocalContext.current.appContainer.vault
    val nav = LocalNav.current
    val items by vault.items.collectAsStateWithLifecycle()
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    val medical by vault.medicalRecords.collectAsStateWithLifecycle()
    val total = (items.size + contacts.size + medical.size).coerceAtLeast(1)
    val isWide = androidx.compose.material3.adaptive.currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != androidx.window.core.layout.WindowWidthSizeClass.COMPACT
    LazyVerticalGrid(GridCells.Fixed(if (isWide) 4 else 2), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Sections.navGroups.forEach { (label, ids) ->
            item(span = { GridItemSpan(maxLineSpan) }) { MonoLabel(label, Modifier.padding(top = 10.dp, start = 2.dp, bottom = 2.dp)) }
            items(ids) { id ->
                val s = Sections[id]; val n = items.count { it.section == id }
                ToolTile(s.label, s.icon, Accents.byName(s.color), n, n / total.toFloat(), iconSize = SPACES_ICON) { nav.navigate(Routes.section(id)) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { MonoLabel("People & health", Modifier.padding(top = 10.dp, start = 2.dp, bottom = 2.dp)) }
        item { ToolTile("Contacts", Icons.Outlined.ContactPage, Accents.sky, contacts.size, contacts.size / total.toFloat(), unit = "people", iconSize = SPACES_ICON) { nav.navigate(Routes.CONTACTS) } }
        item { ToolTile("Medical", Icons.Outlined.MonitorHeart, Accents.rose, medical.size, medical.size / total.toFloat(), unit = "records", iconSize = SPACES_ICON) { nav.navigate(Routes.MEDICAL) } }
        item { ToolTile("Timeline", Icons.Outlined.EventNote, Accents.amber, 0, 0f, unit = "events", iconSize = SPACES_ICON) { nav.navigate(Routes.TIMELINE) } }
        item { ToolTile("Cards", Icons.Outlined.CreditCard, Accents.violet, 0, 0f, unit = "cards", iconSize = SPACES_ICON) { nav.navigate(Routes.CARDS) } }
    }
}

/* ───────────────────────── More ───────────────────────── */

@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    val vault = LocalContext.current.appContainer.vault
    val storage by vault.storage.collectAsStateWithLifecycle()
    val accents = listOf(Accents.sky, Accents.violet, Accents.emerald, Accents.amber, Accents.fuchsia, Accents.cyan, Accents.rose, Accents.indigo)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 24.dp)) {
        item {
            BentoCard(padding = 6.dp) {
                Routes.secondary.forEachIndexed { i, entry ->
                    if (i > 0) HairLine(Modifier.padding(horizontal = 8.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(entry.route) }.padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(entry.icon, accents[i % accents.size], size = 39.dp, radius = 11.dp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                            if (entry.route == Routes.BILLING && storage != null) Text("${Files.formatSize(storage!!.bytesUsed)} of ${storage!!.storageLimitGb} GB · ${storage!!.planName}", style = MonoCaption.copy(letterSpacing = 0.3.sp), color = Bento.mutedFg, modifier = Modifier.padding(top = 2.dp))
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Bento.subtleFg)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)); Text("ADMINISTRATION IS MANAGED FROM THE PERSORA WEB CONSOLE.", style = MonoCaption, color = Bento.subtleFg, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

/* ───────────────────────── Notifications ───────────────────────── */

@Composable
fun NotificationsScreen() {
    val vault = LocalContext.current.appContainer.vault
    val nav = LocalNav.current
    val notifications by vault.notifications.collectAsStateWithLifecycle()
    val items by vault.items.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vault.markNotificationsRead() }
    if (notifications.isEmpty()) { EmptyState("You're all caught up.", "Reminders, alarms and sharing activity will show up here.", Icons.Outlined.NotificationsNone); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(notifications.size) { i ->
            val n = notifications[i]
            val (icon, accent) = when (n.kind) { "reminder" -> Icons.Outlined.NotificationsActive to Accents.amber; "alarm" -> Icons.Outlined.Alarm to Accents.violet; else -> Icons.Outlined.Share to Accents.sky }
            BentoCard(padding = 12.dp, radius = 16.dp, onClick = {
                val scheduleItem = if (n.kind == "reminder" || n.kind == "alarm") items.firstOrNull { n.id.startsWith("schedule:${it.id}:") } else null
                when { scheduleItem != null -> Details.openItem(scheduleItem.id); n.kind == "reminder" || n.kind == "alarm" -> nav.navigate(Routes.section("notes")); else -> nav.navigate(Routes.SHARED) }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(icon, accent, size = 32.dp, radius = 10.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(n.actorName, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)); Pill(n.kind, accent.toTone()) }
                        Text(n.message, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                    }
                    Text(Dates.formatRelative(n.createdAt), style = MonoCaption.copy(letterSpacing = 0.3.sp), color = Bento.subtleFg)
                }
            }
        }
    }
}
