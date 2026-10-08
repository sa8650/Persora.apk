package app.persora.android.ui.vault

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import app.persora.android.core.util.Qr
import app.persora.android.data.model.FieldKind
import app.persora.android.data.model.Sections
import app.persora.android.data.model.VaultItem
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.EditorDrawer
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ItemDetailDialog from VaultDialogs.tsx as a full screen with file preview, share, QR and record actions. */
@Composable
fun ItemDetailScreen(itemId: String, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val api = container.api
    val nav = LocalNav.current
    val close: () -> Unit = onClose ?: { nav.popBackStack() }
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val items by vault.items.collectAsStateWithLifecycle()
    val incoming by vault.incomingShares.collectAsStateWithLifecycle()
    val outgoing by vault.outgoingShares.collectAsStateWithLifecycle()
    val foldersMap by vault.folders.collectAsStateWithLifecycle()
    val shared = incoming.firstOrNull { it.item.id == itemId }
    val item = items.firstOrNull { it.id == itemId } ?: shared?.item
    var confirmDelete by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    if (item == null) { EmptyState("Record not found", "It may have been deleted on another device.", Icons.Outlined.SearchOff) { QuietButton("Go back", onClick = { close() }) }; return }
    val section = Sections[item.section]
    val tone = Tones.byName(section.color)
    val readOnly = shared != null && shared.permission == "view"
    val folders = foldersMap[item.section].orEmpty()
    val myShares = outgoing.filter { it.item.id == item.id }

    fun openFile(share: Boolean) {
        val key = item.file?.key ?: return
        busy = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { api.openVaultFile(key).use { dl -> Files.stash(context, dl.name, dl.stream) } }
                val mime = item.file.type ?: "application/octet-stream"
                context.startActivity(if (share) Files.shareIntent(uri, mime, item.title) else Files.viewIntent(uri, mime))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Could not open the file.", true) } finally { busy = false }
        }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentWindowInsets = if (onClose != null) WindowInsets(0.dp) else ScaffoldDefaults.contentWindowInsets, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(if (onClose != null) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Bento.mutedFg) }
            Spacer(Modifier.weight(1f))
            if (!readOnly) IconButton(onClick = { scope.launch { runCatchingSafe { vault.toggleFavorite(item) }.onFailure { notify(it.message ?: "Could not update.", true) } } }) { Icon(if (item.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favorite", tint = if (item.favorite) Accents.amber.c500 else Bento.mutedFg) }
            if (!readOnly) IconButton(onClick = { onClose?.invoke(); EditorDrawer.openItem(item.section, item.id, shareId = shared?.shareId) }) { Icon(Icons.Outlined.Edit, "Edit", tint = Bento.mutedFg) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = Bento.mutedFg) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Bento.card) {
                    if (shared == null) DropdownMenuItem(text = { Text(if (item.pinned) "Unpin" else "Pin to top") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) }, onClick = { menu = false; scope.launch { runCatchingSafe { vault.togglePinned(item) } } })
                    if (shared == null) DropdownMenuItem(text = { Text("Share with a member") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; shareOpen = true })
                    if (item.section == "memberships" || item.section == "urls") DropdownMenuItem(text = { Text("Show QR code") }, leadingIcon = { Icon(Icons.Outlined.QrCode2, null) }, onClick = { menu = false; qrPayload = if (item.section == "urls") item.metadata["url"].orEmpty() else "PERSORA-MEMBER:${item.metadata["memberId"].orEmpty()}:${item.title}" })
                    if (shared == null && folders.isNotEmpty()) {
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("No folder", color = if (item.folderId == null) Bento.primary else Bento.fg) }, onClick = { menu = false; scope.launch { runCatchingSafe { vault.moveToFolder(item, null) } } })
                        folders.forEach { f -> DropdownMenuItem(text = { Text(f.name, color = if (item.folderId == f.id) Bento.primary else Bento.fg) }, leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = Tones.byName(f.color).fg) }, onClick = { menu = false; scope.launch { runCatchingSafe { vault.moveToFolder(item, f.id) } } }) }
                    }
                    if (shared == null) { HorizontalDivider(); DropdownMenuItem(text = { Text("Delete", color = Bento.danger) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Bento.danger) }, onClick = { menu = false; confirmDelete = true }) }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 60.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIconBox(section.icon, tone, size = 48.dp, radius = 14.dp); Spacer(Modifier.width(14.dp))
                Column { Eyebrow(section.eyebrow); Text(item.title, style = MaterialTheme.typography.headlineSmall, color = Bento.fg) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(section.label, tone)
                if (item.pinned) Pill("Pinned", Tones.Blue, Icons.Outlined.PushPin)
                if (shared != null) Pill("Shared by ${shared.owner.fullName} · ${shared.permission}", Tones.Amber, Icons.Outlined.Share)
                item.folderId?.let { id -> folders.firstOrNull { it.id == id }?.let { Pill(it.name, Tones.byName(it.color), Icons.Outlined.Folder) } }
                section.dateKey?.let { Dates.daysUntil(item.metadata[it]) }?.let { d -> Pill(when { d < 0 -> "Expired ${-d}d ago"; d == 0L -> "Expires today"; else -> "Expires in ${d}d" }, when { d < 0 -> Tones.Red; d <= 30 -> Tones.Amber; else -> Tones.Green }, Icons.Outlined.Event) }
            }
            Spacer(Modifier.height(16.dp))

            if (item.section == "wallet-cards") { WalletCardTile(item) {}; Spacer(Modifier.height(14.dp)) }

            item.file?.let { file ->
                BentoCard(padding = 12.dp) {
                    if (file.key != null && (Files.isPdf(file.type, file.name) || Files.isText(file.type, file.name))) {
                        // Inline Drive-style preview — rendered on-device with PdfRenderer / plain text, no external viewer needed.
                        InlineFilePreview(file.key, file.name, file.type, download = { key -> api.openVaultFile(key).stream })
                        Spacer(Modifier.height(10.dp))
                    } else if (Files.isImage(file.type) && file.key != null) {
                        AsyncImage(model = container.client.url("/file?key=${Uri.encode(file.key)}"), contentDescription = file.name, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)).background(Bento.muted), contentScale = ContentScale.Fit)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIconBox(if (Files.isPdf(file.type)) Icons.Outlined.PictureAsPdf else if (Files.isImage(file.type)) Icons.Outlined.Image else Icons.Outlined.InsertDriveFile, Tones.Blue, size = 38.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(file.name, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1); Text(listOfNotNull(file.type, Files.formatSize(file.size).takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton(if (busy) "Opening…" else "Open", onClick = { if (!busy) openFile(false) }, icon = Icons.Outlined.OpenInNew)
                        QuietButton("Share file", onClick = { if (!busy) openFile(true) }, icon = Icons.Outlined.IosShare)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (item.isTodo || item.isSchedule) {
                BentoCard {
                    if (item.isTodo) { DetailRow("Status", if (item.metadata["completed"] == "true") "Completed" else "Active"); DetailRow("Due date", Dates.formatDate(item.metadata["dueDate"])); DetailRow("Details", item.metadata["todoDetails"].orEmpty()) }
                    if (item.isReminder) { DetailRow("Remind me at", Dates.formatDateTime(item.metadata["reminderAt"])); DetailRow("Ringtone", item.metadata["ringtoneName"].orEmpty()); DetailRow("Status", if (item.metadata["enabled"] == "false") "Paused" else "Active") }
                    if (item.isAlarm) { DetailRow("Alarm time", Dates.formatClock(item.metadata["alarmTime"].orEmpty())); DetailRow("Repeats", Dates.repeatLabel(item.metadata["repeatDays"])); DetailRow("One-time date", Dates.formatDate(item.metadata["alarmDate"])); DetailRow("Next ring", Dates.nextScheduleDate(item)?.let { "${Dates.formatTime(it)} · ${it.toLocalDate()}" }.orEmpty()); DetailRow("Ringtone", item.metadata["ringtoneName"].orEmpty()) }
                    item.metadata["snoozedUntil"]?.let { DetailRow("Snoozed until", Dates.formatDateTime(it)) }
                }
                Spacer(Modifier.height(14.dp))
            }

            val hidden = setOf("recordType", "addFlowType", "completed", "todoDetails", "dueDate", "reminderAt", "alarmTime", "alarmDate", "repeatDays", "ringtoneId", "ringtoneName", "enabled", "snoozedUntil", "relatedItemIds", "relatedContactIds", "color")
            val fieldsByKey = section.fields.associateBy { it.key }
            val details = item.metadata.filter { (k, v) -> v.isNotBlank() && k !in hidden && k != "notes" && !(item.section == "notes" && k == "content") }
            val savedAddFlowType = item.metadata["addFlowType"]?.takeIf { it.isNotBlank() }
            if (details.isNotEmpty() || savedAddFlowType != null) BentoCard {
                savedAddFlowType?.let { DetailRow("Type", it) }
                details.forEach { (key, value) ->
                    val def = fieldsByKey[key]
                    val label = def?.label ?: key.replace(Regex("([A-Z])"), " $1").replaceFirstChar { it.uppercase() }
                    when {
                        key == "member" -> {
                            val familyName = when {
                                value.equals("me", ignoreCase = true) -> "Me"
                                else -> items.firstOrNull { it.section == "family" && it.id == value }?.title
                                    ?: if (value.length >= 30 && value.contains('-')) "Family member" else value
                            }
                            DetailRow("Belongs to", familyName)
                        }
                        def?.kind == FieldKind.DATE -> DetailRow(label, Dates.formatDate(value))
                        def?.kind == FieldKind.URL -> LinkRow(label, value) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (value.startsWith("http")) value else "https://$value"))) }
                        def?.kind == FieldKind.EMAIL -> LinkRow(label, value) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$value"))) }
                        else -> DetailRow(label, value)
                    }
                }
            }
            val longText = if (item.section == "notes") item.metadata["content"] else item.metadata["notes"]
            val noteTint = if (item.section == "notes") app.persora.android.ui.theme.NoteColors.background(item.metadata["color"]) else null
            if (!longText.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                if (noteTint != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(noteTint).padding(16.dp)) { Eyebrow("Note", app.persora.android.ui.theme.NoteColors.onTintMuted); Spacer(Modifier.height(6.dp)); androidx.compose.foundation.text.selection.SelectionContainer { Text(longText, style = MaterialTheme.typography.bodyMedium, color = app.persora.android.ui.theme.NoteColors.onTint) } }
                else BentoCard { Eyebrow(if (item.section == "notes") "Note" else "Notes", Bento.subtleFg); Spacer(Modifier.height(6.dp)); androidx.compose.foundation.text.selection.SelectionContainer { Text(longText, style = MaterialTheme.typography.bodyMedium, color = Bento.fg) } }
            }

            if (myShares.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                BentoCard {
                    SectionHeading("Shared with", "${myShares.size} member${if (myShares.size == 1) "" else "s"}")
                    myShares.forEach { s ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(s.recipient.fullName.take(2).uppercase(), 30.dp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(s.recipient.fullName, style = MaterialTheme.typography.titleSmall); Text("${s.recipient.email} · can ${s.permission}", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
                            TextButton(onClick = { scope.launch { runCatchingSafe { api.revokeDocumentShare(s.shareId); vault.refreshShares() }.onSuccess { notify("Access removed.", false) }.onFailure { notify(it.message ?: "Could not revoke.", true) } } }) { Text("Revoke", color = Bento.danger) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Created ${Dates.formatDate(item.createdAt)} · Updated ${Dates.formatRelative(item.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
        }
    }

    if (confirmDelete) ConfirmDialog("Delete this ${section.singular}?", "This removes the record${if (item.file != null) " and its attached file" else ""} from your vault on every device.", onConfirm = {
        confirmDelete = false; scope.launch { runCatchingSafe { vault.deleteItem(item) }.onSuccess { notify("Deleted.", false); close() }.onFailure { notify(it.message ?: "Could not delete.", true) } }
    }, onDismiss = { confirmDelete = false })
    if (shareOpen) ShareDialog(onDismiss = { shareOpen = false }) { recipient, permission ->
        scope.launch { runCatchingSafe { api.createDocumentShare(item.id, recipient, permission); vault.refreshShares() }.onSuccess { notify("Shared with $recipient.", false) }.onFailure { notify(it.message ?: "Could not share.", true) }; shareOpen = false }
    }
    qrPayload?.let { QrDialog(if (item.section == "urls") "Open this link" else "Membership code", it, if (item.section == "urls") item.metadata["url"].orEmpty() else "Generated on this device · ${item.title}", onDismiss = { qrPayload = null }) }
}

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Bento.primary, fontWeight = FontWeight.Medium)
    }
}

/** ShareRecordDialog.tsx: recipient by email or seven-digit Persora ID, permission view/comment/edit. */
@Composable
fun ShareDialog(allowPermission: Boolean = true, onDismiss: () -> Unit, onShare: (recipient: String, permission: String) -> Unit) {
    var recipient by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("view") }
    val vault = LocalContext.current.appContainer.vault
    val outgoing by vault.outgoingShares.collectAsStateWithLifecycle()
    val outgoingRecords by vault.outgoingRecordShares.collectAsStateWithLifecycle()
    // Members you shared with before (most recent first) — tap to fill the recipient field.
    val recent = remember(outgoing, outgoingRecords) {
        (outgoing.map { it.recipient to it.createdAt } + outgoingRecords.map { it.recipient to it.createdAt })
            .filter { it.first.userId.isNotBlank() || it.first.email.isNotBlank() }
            .sortedByDescending { it.second }.map { it.first }.distinctBy { it.userId.ifBlank { it.email } }.take(8)
    }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(22.dp), containerColor = Bento.card, title = { Text("Share with a Persora member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(recipient, { recipient = it }, "Email or Persora ID", placeholder = "name@example.com or 1234567")
                if (recent.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoLabel("Recently shared with")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { u ->
                            val handle = u.userId.ifBlank { u.email }
                            val selected = recipient.trim() == handle
                            Row(
                                Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Bento.primarySoft else Bento.muted).border(1.dp, if (selected) Bento.primary else Bento.border, RoundedCornerShape(10.dp)).clickable { recipient = handle }.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Avatar(u.fullName.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }, 24.dp)
                                Column { Text(u.fullName.ifBlank { handle }, style = MaterialTheme.typography.labelLarge, color = Bento.fg, maxLines = 1); Text(if (u.userId.isNotBlank()) "ID ${u.userId}" else u.email, style = app.persora.android.ui.theme.MonoCaption.copy(letterSpacing = 0.3.sp), color = Bento.mutedFg, maxLines = 1) }
                            }
                        }
                    }
                }
                if (allowPermission) SegmentedTabs(listOf("View", "Comment", "Edit"), listOf("view", "comment", "edit").indexOf(permission), { permission = listOf("view", "comment", "edit")[it] })
                Text("Recipients see this record under Shared with me. You can change or revoke access at any time.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
            }
        },
        confirmButton = { TextButton(onClick = { if (recipient.isNotBlank()) onShare(recipient.trim(), permission) }) { Text("Share", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Bento.mutedFg) } })
}
