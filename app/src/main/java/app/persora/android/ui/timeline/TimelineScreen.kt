package app.persora.android.ui.timeline

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import app.persora.android.core.util.PickedFile
import app.persora.android.data.model.TimelineEvent
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.Details
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.UUID

/** LifeTimelineView.tsx: automatic events (from records) and manual milestones on one vertical timeline, grouped by month. */
@Composable
fun TimelineScreen() {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val events by vault.timeline.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 milestones, 2 automatic
    var editing by remember { mutableStateOf<TimelineEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TimelineEvent?>(null) }
    LaunchedEffect(Unit) { vault.refreshTimeline() }

    val visible = remember(events, filter) { events.filter { when (filter) { 1 -> it.eventType == "manual"; 2 -> it.eventType != "manual"; else -> true } }.sortedByDescending { it.eventDate } }
    val grouped = remember(visible) { visible.groupBy { Dates.parseLocalDate(it.eventDate)?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: Dates.parseInstant(it.eventDate)?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: "Undated" } }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }, containerColor = Bento.primary, contentColor = Bento.primaryFg, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 96.dp)) {
            item {
                SegmentedTabs(listOf("All", "Milestones", "From records"), filter, { filter = it }); Spacer(Modifier.height(6.dp))
            }
            if (visible.isEmpty()) item { EmptyState("Nothing on the timeline yet.", "Add a milestone, or save records with dates and they appear here automatically.", Icons.Outlined.EventNote) }
            grouped.forEach { (month, list) ->
                item { Text(month.uppercase(), style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                items(list, key = { it.id }) { e ->
                    val manual = e.eventType == "manual"
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(if (manual) Bento.primary else Bento.borderStrong))
                            Box(Modifier.weight(1f).width(2.dp).background(Bento.border))
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 12.dp)) {
                            Text(Dates.formatDate(e.eventDate), style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg)
                            BentoCard(padding = 12.dp, onClick = { if (manual) editing = e else e.recordId?.let { Details.openItem(it) } }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(e.title, style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                                        if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                    Pill(if (manual) "Milestone" else "Auto", if (manual) Tones.Blue else Tones.Neutral)
                                }
                                if (e.url != null || e.attachment != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    e.url?.takeIf { it.isNotBlank() }?.let { u -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (u.startsWith("http")) u else "https://$u"))) }, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Outlined.Link, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Open link") } }
                                    e.attachment?.let { a -> TextButton(onClick = { scope.launch { runCatchingSafe { val uri = withContext(Dispatchers.IO) { container.api.openTimelineAttachment(e.id).use { Files.stash(context, it.name, it.stream) } }; context.startActivity(Files.viewIntent(uri, a.type)) }.onFailure { notify(it.message ?: "Could not open.", true) } } }, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Outlined.AttachFile, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) TimelineEditorSheet(editing, onDismiss = { creating = false; editing = null }, onDelete = { confirmDelete = it; editing = null })
    confirmDelete?.let { e -> ConfirmDialog("Remove this milestone?", "“${e.title}” is removed from your timeline.", onConfirm = { confirmDelete = null; scope.launch { runCatchingSafe { vault.deleteTimelineEvent(e.id) }.onSuccess { notify("Milestone removed.", false) }.onFailure { notify(it.message ?: "Could not delete.", true) } } }, onDismiss = { confirmDelete = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineEditorSheet(existing: TimelineEvent?, onDismiss: () -> Unit, onDelete: (TimelineEvent) -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var date by remember { mutableStateOf(existing?.eventDate?.take(10) ?: Dates.today()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var removeAttachment by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { picked = Files.describe(context, it); removeAttachment = false } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Bento.card, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Life timeline"); Text(if (existing == null) "Add a milestone" else "Edit milestone", style = MaterialTheme.typography.titleLarge, color = Bento.fg)
            TextInput(title, { title = it }, "What happened?", required = true, placeholder = "Graduated, moved house, first job…")
            DateInput(date, { date = it }, "Date", required = true)
            TextInput(description, { description = it }, "Story", minLines = 3)
            TextInput(url, { url = it }, "Link", keyboard = KeyboardType.Uri, placeholder = "https://")
            val current = picked?.name ?: existing?.attachment?.takeIf { !removeAttachment }?.name
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoftButton(if (current == null) "Attach a photo or file" else "Replace file", onClick = { picker.launch(arrayOf("*/*")) }, icon = Icons.Outlined.AttachFile)
                if (current != null) { Spacer(Modifier.width(8.dp)); Text(current, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(onClick = { picked = null; removeAttachment = true }) { Icon(Icons.Outlined.Close, "Remove", tint = Bento.danger) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Save", onClick = {
                    if (title.isBlank() || date.isBlank()) { notify("Add a title and date.", true); return@PrimaryButton }
                    saving = true
                    scope.launch {
                        try {
                            var attachment = if (removeAttachment) null else existing?.attachment
                            picked?.let { p -> attachment = withContext(Dispatchers.IO) { container.api.uploadTimelineAttachment(p.name, p.mime, p.size) { p.open(context) } } }
                            val draft = (existing ?: TimelineEvent(id = UUID.randomUUID().toString(), eventDate = date, title = "")).copy(eventType = "manual", eventDate = date, title = title.trim(), description = description.trim(), url = url.trim().ifBlank { null }, attachment = attachment)
                            vault.saveTimelineEvent(draft, existing == null, removeAttachment && picked == null)
                            notify("Milestone saved.", false); onDismiss()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Could not save.", true) } finally { saving = false }
                    }
                }, enabled = !saving, loading = saving)
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("Delete", color = Bento.danger, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
