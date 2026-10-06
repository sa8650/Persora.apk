package app.persora.android.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.data.model.Sections
import app.persora.android.data.model.VaultFolder
import app.persora.android.data.model.VaultItem
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.Details
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.MonoCaption
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.launch

/**
 * One vault page (SectionView in Workspace.tsx): folder shelf, search, sort, favorites filter and the item list.
 * `notes` gets the Tasks / Reminders / Alarms tabs; `wallet-cards` renders masked card visuals; `personal-finance` shows totals.
 */
@Composable
fun SectionScreen(sectionId: String) {
    val section = Sections[sectionId]
    val context = LocalContext.current
    val vault = context.appContainer.vault
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val allItems by vault.items.collectAsStateWithLifecycle()
    val foldersMap by vault.folders.collectAsStateWithLifecycle()
    val folders = foldersMap[sectionId].orEmpty()
    var query by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf<String?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(0) } // 0 recent, 1 A-Z, 2 date
    var notesTab by remember { mutableStateOf(0) } // notes section only
    // Every record view opens in the app-wide bottom drawer.
    val openItem: (String) -> Unit = { id -> Details.openItem(id) }
    var folderDialog by remember { mutableStateOf<VaultFolder?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }

    LaunchedEffect(sectionId) { vault.refreshFolders(sectionId) }

    val sectionItems = remember(allItems, sectionId) { allItems.filter { it.section == sectionId } }
    val visible = remember(sectionItems, query, folderId, favoritesOnly, sort, notesTab) {
        var list = sectionItems
        if (sectionId == "notes") list = list.filter { when (notesTab) { 0 -> it.recordType.isBlank() || it.recordType == "note"; 1 -> it.isTodo; 2 -> it.isReminder; else -> it.isAlarm } }
        if (folderId != null) list = list.filter { it.folderId == folderId }
        if (favoritesOnly) list = list.filter { it.favorite }
        if (query.isNotBlank()) { val q = query.lowercase(); list = list.filter { it.title.lowercase().contains(q) || it.metadata.values.any { v -> v.lowercase().contains(q) } } }
        when (sort) {
            1 -> list.sortedBy { it.title.lowercase() }
            2 -> list.sortedBy { it.metadata[section.dateKey ?: "dueDate"] ?: "9999" }
            else -> list.sortedWith(compareByDescending<VaultItem> { it.pinned }.thenByDescending { it.updatedAt })
        }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = {
            val kind = if (sectionId == "notes") listOf("note", "todo", "reminder", "alarm")[notesTab] else null
            nav.navigate(Routes.editor(sectionId, folder = folderId, kind = kind))
        }, containerColor = Bento.primary, contentColor = Bento.primaryFg, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add") })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SearchField(query, { query = it }, "Search ${section.label.lowercase()}…") }
            if (sectionId == "accounts" || sectionId == "wallet-cards") item {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Accents.amber.soft).border(1.dp, Accents.amber.line, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, null, tint = Accents.amber.text, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (sectionId == "accounts") "Never store passwords, PINs, CVVs or one-time codes here." else "Only the brand and last four digits are saved—never the full number or CVV.", style = MaterialTheme.typography.bodySmall, color = Accents.amber.text)
                }
            }
            if (sectionId == "notes") item { SegmentedTabs(listOf("Notes", "Tasks", "Reminders", "Alarms"), notesTab, { notesTab = it }) }
            if (sectionId == "personal-finance") item { FinanceSummary(sectionItems) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("Favorites") }, leadingIcon = { Icon(if (favoritesOnly) Icons.Outlined.Star else Icons.Outlined.StarBorder, null, Modifier.size(16.dp)) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bento.muted, selectedLabelColor = Bento.primary, selectedLeadingIconColor = Bento.primary))
                    listOf("Recent", "A–Z", if (section.dateKey != null) "By date" else null).forEachIndexed { i, label -> if (label != null) FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(label) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bento.muted, selectedLabelColor = Bento.primary)) }
                }
            }
            item {
                FolderShelf(folders, folderId, onSelect = { folderId = it }, onCreate = { creatingFolder = true }, onLongPress = { folderDialog = it }, counts = sectionItems.groupingBy { it.folderId }.eachCount())
            }
            if (visible.isEmpty()) item {
                EmptyState(
                    title = if (sectionItems.isEmpty()) "No ${section.label.lowercase()} yet." else "Nothing matches.",
                    body = if (sectionItems.isEmpty()) "Add your first ${section.singular} and it syncs to the website instantly." else "Try a different search, folder or filter.",
                    icon = section.icon,
                )
            }
            items(visible, key = { it.id }) { item ->
                when {
                    sectionId == "wallet-cards" -> WalletCardTile(item) { openItem(item.id) }
                    item.isTodo -> TodoRow(item, onToggle = { done -> scope.launch { runCatchingSafe { vault.setTodoCompleted(item, done) }.onFailure { notify(it.message ?: "Could not update task.", true) } } }) { openItem(item.id) }
                    item.isSchedule -> ScheduleCard(item, onToggle = { enabled -> scope.launch { runCatchingSafe { vault.setScheduleEnabled(item, enabled) }.onFailure { notify(it.message ?: "Could not update.", true) } } }) { openItem(item.id) }
                    item.section == "notes" -> NoteCard(item) { openItem(item.id) }
                    else -> ItemCard(item) { openItem(item.id) }
                }
            }
        }
    }

    if (creatingFolder || folderDialog != null) FolderDialog(folderDialog, scope = sectionId, onDismiss = { creatingFolder = false; folderDialog = null },
        onSave = { id, name, color, pinned -> scope.launch { runCatchingSafe { vault.saveFolder(id, sectionId, name, color, pinned) }.onSuccess { notify("Folder saved.", false) }.onFailure { notify(it.message ?: "Could not save folder.", true) }; creatingFolder = false; folderDialog = null } },
        onDelete = { f -> scope.launch { runCatchingSafe { vault.deleteFolder(f) }.onSuccess { if (folderId == f.id) folderId = null; notify("Folder removed. Its records stay in ${section.label}.", false) }.onFailure { notify(it.message ?: "Could not delete folder.", true) }; folderDialog = null } })

}

/* ---------------- Rows / cards ---------------- */

/** Keep-style note card: tinted with the note's colour, title + body preview + tag pills. */
@Composable
fun NoteCard(item: VaultItem, onClick: () -> Unit) {
    val tint = app.persora.android.ui.theme.NoteColors.background(item.metadata["color"])
    val fg = if (tint != null) app.persora.android.ui.theme.NoteColors.onTint else Bento.fg
    val muted = if (tint != null) app.persora.android.ui.theme.NoteColors.onTintMuted else Bento.mutedFg
    val shape = RoundedCornerShape(20.dp)
    val tags = item.metadata["tags"].orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(tint ?: Bento.card).border(1.dp, if (tint != null) Color.Black.copy(alpha = if (Bento.isDark) 0.25f else 0.06f) else Bento.border, shape).clickable(onClick = onClick).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (item.pinned) Icon(Icons.Outlined.PushPin, "Pinned", tint = if (tint != null) fg else Bento.primary, modifier = Modifier.padding(start = 6.dp).size(13.dp))
            if (item.favorite) Icon(Icons.Outlined.Star, "Favorite", tint = Accents.amber.c500, modifier = Modifier.padding(start = 4.dp).size(14.dp))
        }
        val body = item.metadata["content"].orEmpty()
        if (body.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(body, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        if (tags.isNotEmpty() || item.file != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.take(4).forEach { t -> Text(t, style = MaterialTheme.typography.labelSmall, color = fg, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (tint != null) Color.Black.copy(alpha = if (Bento.isDark) 0.22f else 0.07f) else Bento.muted).padding(horizontal = 8.dp, vertical = 4.dp)) }
                if (item.file != null) Pill(item.file.name, Tones.Neutral, Icons.Outlined.AttachFile)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Dates.formatRelative(item.updatedAt), style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

@Composable
fun ItemCard(item: VaultItem, onClick: () -> Unit) {
    val section = Sections[item.section]
    val tone = Tones.byName(section.color)
    val preview = section.previewKeys.mapNotNull { key -> item.metadata[key]?.takeIf { it.isNotBlank() }?.let { key to it } }
    val expiry = section.dateKey?.let { Dates.daysUntil(item.metadata[it]) }
    BentoCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            ToneIconBox(section.icon, tone, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item.pinned) Icon(Icons.Outlined.PushPin, "Pinned", tint = Bento.primary, modifier = Modifier.padding(start = 6.dp).size(13.dp))
                    if (item.favorite) Icon(Icons.Outlined.Star, "Favorite", tint = Accents.amber.c500, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
                val subtitle = preview.firstOrNull()?.second ?: item.subtitle.orEmpty()
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (preview.size > 1 || item.file != null || expiry != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        preview.drop(1).forEach { (key, value) -> Pill(if (section.fields.firstOrNull { it.key == key }?.kind == app.persora.android.data.model.FieldKind.DATE) Dates.formatDate(value) else value) }
                        if (item.file != null) Pill(item.file.name, Tones.Neutral, Icons.Outlined.AttachFile)
                        if (expiry != null) Pill(when { expiry < 0 -> "Expired ${-expiry}d ago"; expiry == 0L -> "Expires today"; expiry <= 30 -> "Expires in ${expiry}d"; else -> "Valid" }, when { expiry < 0 -> Tones.Red; expiry <= 30 -> Tones.Amber; else -> Tones.Green })
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(item: VaultItem, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val done = item.metadata["completed"] == "true"
    val due = item.metadata["dueDate"]
    BentoCard(onClick = onClick, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = done, onCheckedChange = onToggle, colors = CheckboxDefaults.colors(checkedColor = Bento.primary))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = if (done) Bento.subtleFg else Bento.fg, textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.metadata["todoDetails"]?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (!due.isNullOrBlank()) { val days = Dates.daysUntil(due) ?: 0; Pill(Dates.formatShort(due), if (done) Tones.Neutral else if (days < 0) Tones.Red else if (days <= 2) Tones.Amber else Tones.Blue, Icons.Outlined.Event) }
        }
    }
}

@Composable
private fun ScheduleCard(item: VaultItem, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val isAlarm = item.isAlarm
    val enabled = item.metadata["enabled"] != "false"
    val next = Dates.nextScheduleDate(item)
    val repeat = Dates.repeatLabel(item.metadata["repeatDays"])
    val timeLabel = if (isAlarm) Dates.formatClock(item.metadata["alarmTime"].orEmpty()) else Dates.formatTime(next)
    val dateLabel = if (isAlarm) (if (repeat.isNotBlank()) "Repeats $repeat" else item.metadata["alarmDate"]?.takeIf { it.isNotBlank() }?.let { Dates.formatDate(it) } ?: "One-time alarm") else next?.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")) ?: "Date unavailable"
    BentoCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIconBox(if (isAlarm) Icons.Outlined.Alarm else Icons.Outlined.NotificationsActive, if (!enabled) Tones.Neutral else if (isAlarm) Tones.Purple else Tones.Orange, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(timeLabel.ifBlank { "—" }, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Bento.fg else Bento.subtleFg, letterSpacing = (-0.8).sp)
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Bento.fg else Bento.subtleFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateLabel + (item.metadata["ringtoneName"]?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
            }
            Switch(checked = enabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Bento.primary))
        }
    }
}

/** Masked payment-card visual (.wallet-card-face on the web): brand gradient, chip, network mark, masked number. */
@Composable
fun WalletCardTile(item: VaultItem, onClick: () -> Unit) {
    val m = item.metadata
    val network = m["network"].orEmpty().ifBlank { "Other" }
    val gradient = when (network) {
        "Visa" -> listOf(Color(0xFF123F88), Color(0xFF1663B9), Color(0xFF378DE0))
        "Mastercard" -> listOf(Color(0xFF101A2B), Color(0xFF26364B), Color(0xFF4B2D3D))
        "American Express" -> listOf(Color(0xFF075988), Color(0xFF126FB7), Color(0xFF2494C5))
        "UnionPay" -> listOf(Color(0xFF31506C), Color(0xFF456A9B), Color(0xFF668BB5))
        "Discover" -> listOf(Color(0xFF353D4A), Color(0xFF4C5362), Color(0xFFA36332))
        else -> listOf(Color(0xFF283648), Color(0xFF465569), Color(0xFF64748B))
    }
    val lastFour = m["lastFour"].orEmpty().takeIf { it.length == 4 && it.all { c -> c.isDigit() } } ?: "••••"
    val expiry = app.persora.android.core.util.Cards.savedExpiry(m["expiryMonth"], m["expiryYear"]).ifBlank { "••/••" }
    Box(Modifier.fillMaxWidth().aspectRatio(1.62f).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(gradient)).clickable(onClick = onClick)) {
        // Soft highlight sweep like the web card face.
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(80f, 40f), radius = 520f)))
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m["issuer"].orEmpty().ifBlank { "PERSORA WALLET" }.uppercase(), color = Color.White.copy(alpha = 0.92f), style = MonoCaption.copy(fontSize = 9.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                NetworkMark(network)
            }
            Spacer(Modifier.height(14.dp))
            // EMV chip
            Box(Modifier.size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFFF2D27A), Color(0xFFC9A24A)))).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(6.dp))) {
                Box(Modifier.align(Alignment.Center).size(width = 20.dp, height = 14.dp).border(1.dp, Color(0x66000000), RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.weight(1f))
            Text("••••   ••••   ••••   $lastFour", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { Text("CARDHOLDER", color = Color.White.copy(alpha = 0.6f), style = MonoCaption.copy(fontSize = 7.5.sp)); Text((m["cardholder"].orEmpty().ifBlank { item.title }).uppercase(), color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Column(horizontalAlignment = Alignment.End) { Text("EXPIRES", color = Color.White.copy(alpha = 0.6f), style = MonoCaption.copy(fontSize = 7.5.sp)); Text(expiry, color = Color.White, style = MaterialTheme.typography.labelLarge) }
                if (!m["cardType"].isNullOrBlank()) { Spacer(Modifier.width(12.dp)); Text(m["cardType"].orEmpty().uppercase(), color = Color.White.copy(alpha = 0.75f), style = MonoCaption.copy(fontSize = 8.sp), modifier = Modifier.padding(bottom = 2.dp)) }
            }
        }
    }
}

/** Network logo like `.wallet-card-network-mark`: Mastercard's two overlapping discs, Amex boxed wordmark, Visa / UnionPay / Discover wordmarks. */
@Composable
fun NetworkMark(network: String, compact: Boolean = false) {
    val scale = if (compact) 0.72f else 1f
    when (network) {
        "Mastercard" -> Box(Modifier.size(width = 37.dp * scale, height = 23.dp * scale)) {
            Box(Modifier.align(Alignment.CenterStart).size(21.dp * scale).clip(CircleShape).background(Color(0xFFEF332C)))
            Box(Modifier.align(Alignment.CenterEnd).size(21.dp * scale).clip(CircleShape).background(Color(0xFFF4A52C).copy(alpha = 0.92f)))
        }
        "American Express" -> Text("AMEX", color = Color.White, fontSize = 9.sp * scale, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp * scale, vertical = 3.dp * scale))
        "Visa" -> Text("VISA", color = Color.White, fontSize = 17.sp * scale, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, letterSpacing = (-0.5).sp)
        "Discover" -> Row(verticalAlignment = Alignment.CenterVertically) { Text("DISC", color = Color.White, fontSize = 11.sp * scale, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp); Box(Modifier.size(11.dp * scale).clip(CircleShape).background(Color(0xFFF58220))); Text("VER", color = Color.White, fontSize = 11.sp * scale, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) }
        "UnionPay" -> Row { listOf(Color(0xFFD10429), Color(0xFF022E64), Color(0xFF007B84)).forEach { c -> Box(Modifier.size(width = 11.dp * scale, height = 20.dp * scale).padding(horizontal = 0.5.dp).clip(RoundedCornerShape(3.dp)).background(c)) } }
        else -> Text(network.uppercase(), color = Color.White, fontSize = 10.sp * scale, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun FinanceSummary(items: List<VaultItem>) {
    fun total(type: String) = items.filter { it.metadata["financeType"] == type }.sumOf { it.metadata["amount"]?.replace(",", "")?.toDoubleOrNull() ?: 0.0 }
    val currency = items.firstOrNull()?.metadata?.get("currency")?.takeIf { it.isNotBlank() } ?: "BDT"
    val cards = listOf(Triple("Income", total("income"), Tones.Green), Triple("Expenses", total("expense"), Tones.Red), Triple("Assets", total("asset"), Tones.Blue), Triple("Debt", items.filter { it.metadata["financeType"] == "loan" }.sumOf { (it.metadata["outstandingAmount"] ?: it.metadata["amount"])?.replace(",", "")?.toDoubleOrNull() ?: 0.0 }, Tones.Amber))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.forEach { (label, amount, tone) ->
            Column(Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).background(tone.bg).border(1.dp, tone.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = tone.fg)
                Text("$currency ${String.format(java.util.Locale.US, "%,.0f", amount)}", style = MaterialTheme.typography.titleMedium, color = Bento.fg)
            }
        }
    }
}

/* ---------------- Folders (VaultFolderShelf.tsx) ---------------- */

@Composable
fun FolderShelf(folders: List<VaultFolder>, selected: String?, onSelect: (String?) -> Unit, onCreate: () -> Unit, onLongPress: (VaultFolder) -> Unit, counts: Map<String?, Int>) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FolderChip("All", Icons.Outlined.FolderOpen, Tones.Blue, selected == null, counts.values.sum()) { onSelect(null) }
        folders.forEach { f -> FolderChipLong(f, selected == f.id, counts[f.id] ?: 0, onClick = { onSelect(if (selected == f.id) null else f.id) }, onLong = { onLongPress(f) }) }
        AssistChip(onClick = onCreate, label = { Text("New folder") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(16.dp)) }, shape = CircleShape, border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = Bento.borderStrong))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderChipLong(folder: VaultFolder, active: Boolean, count: Int, onClick: () -> Unit, onLong: () -> Unit) {
    val tone = Tones.byName(folder.color)
    Row(Modifier.clip(CircleShape).background(if (active) tone.bg else Bento.card).border(1.dp, if (active) tone.line else Bento.borderStrong, CircleShape).combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (folder.pinned) Icons.Outlined.PushPin else Icons.Outlined.Folder, null, tint = tone.fg, modifier = Modifier.size(15.dp))
        Text(folder.name, style = MaterialTheme.typography.labelLarge, color = if (active) tone.fg else Bento.fg, maxLines = 1)
        Text("$count", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg)
    }
}

@Composable
private fun FolderChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: app.persora.android.ui.theme.Tone, active: Boolean, count: Int, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).background(if (active) tone.bg else Bento.card).border(1.dp, if (active) tone.line else Bento.borderStrong, CircleShape).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = tone.fg, modifier = Modifier.size(15.dp)); Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) tone.fg else Bento.fg); Text("$count", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg)
    }
}

@Composable
fun FolderDialog(folder: VaultFolder?, scope: String, onDismiss: () -> Unit, onSave: (id: String?, name: String, color: String, pinned: Boolean) -> Unit, onDelete: (VaultFolder) -> Unit) {
    var name by remember { mutableStateOf(folder?.name.orEmpty()) }
    var color by remember { mutableStateOf(folder?.color ?: "blue") }
    var pinned by remember { mutableStateOf(folder?.pinned ?: false) }
    val colors = listOf("blue", "sky", "teal", "violet", "amber", "rose", "slate", "mint")
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(22.dp), containerColor = Bento.card,
        title = { Text(if (folder == null) "New folder" else "Edit folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(name, { name = it }, "Folder name", required = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { colors.forEach { c -> val t = Tones.byName(c); Box(Modifier.size(28.dp).clip(CircleShape).background(t.bg).border(if (color == c) 2.dp else 1.dp, if (color == c) t.fg else t.line, CircleShape).clickable { color = c }) } }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pinned, { pinned = it }, colors = CheckboxDefaults.colors(checkedColor = Bento.primary)); Text("Pin this folder first", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(folder?.id, name.trim(), color, pinned) }) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { Row { if (folder != null) TextButton(onClick = { onDelete(folder) }) { Text("Delete", color = Bento.danger) }; TextButton(onClick = onDismiss) { Text("Cancel", color = Bento.mutedFg) } } })
}
