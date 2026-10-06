package app.persora.android.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.data.model.AppUser
import app.persora.android.data.model.Sections
import app.persora.android.data.model.ShareComment
import app.persora.android.data.model.SharedVaultEntry
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.Details
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.launch

/** SharedView.tsx: documents and records shared with me / by me, with comments and permission management. */
@Composable
fun SharedScreen(user: AppUser) {
    val container = LocalContext.current.appContainer
    val vault = container.vault
    val api = container.api
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val incoming by vault.incomingShares.collectAsStateWithLifecycle()
    val outgoing by vault.outgoingShares.collectAsStateWithLifecycle()
    val incomingRecords by vault.incomingRecordShares.collectAsStateWithLifecycle()
    val outgoingRecords by vault.outgoingRecordShares.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var comments by remember { mutableStateOf<SharedVaultEntry?>(null) }
    LaunchedEffect(Unit) { vault.refreshShares() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SegmentedTabs(listOf("Shared with me (${incoming.size + incomingRecords.size})", "Shared by me (${outgoing.size + outgoingRecords.size})"), tab, { tab = it }) }
        val docs = if (tab == 0) incoming else outgoing
        val records = if (tab == 0) incomingRecords else outgoingRecords
        if (docs.isEmpty() && records.isEmpty()) item { EmptyState(if (tab == 0) "Nothing shared with you yet." else "You haven't shared anything.", if (tab == 0) "When a Persora member shares a document, contact or card with you it appears here." else "Open any record and choose Share to give a member view, comment or edit access.", Icons.Outlined.Share) }
        if (docs.isNotEmpty()) item { Text("DOCUMENTS", style = MaterialTheme.typography.labelSmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 6.dp)) }
        items(docs, key = { it.shareId }) { s ->
            val section = Sections[s.item.section]
            val person = if (tab == 0) s.owner else s.recipient
            BentoCard(padding = 12.dp, onClick = { Details.openItem(s.item.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIconBox(section.icon, Tones.byName(section.color), size = 40.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.item.title, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (tab == 0) "From" else "With"} ${person.fullName} · ${Dates.formatRelative(s.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Pill("can ${s.permission}", when (s.permission) { "edit" -> Tones.Green; "comment" -> Tones.Amber; else -> Tones.Neutral })
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.permission != "view" || tab == 1) TextButton(onClick = { comments = s }, contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Comments") }
                    if (tab == 1) {
                        var menu by remember { mutableStateOf(false) }
                        Box { TextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Outlined.Tune, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Permission") }
                            DropdownMenu(menu, { menu = false }, containerColor = Bento.card) { listOf("view", "comment", "edit").forEach { p -> DropdownMenuItem(text = { Text("Can $p", color = if (p == s.permission) Bento.primary else Bento.fg) }, onClick = { menu = false; scope.launch { runCatchingSafe { api.changeSharePermission(s.shareId, p); vault.refreshShares() }.onFailure { notify(it.message ?: "Could not update.", true) } } }) } } }
                        TextButton(onClick = { scope.launch { runCatchingSafe { api.revokeDocumentShare(s.shareId); vault.refreshShares() }.onSuccess { notify("Access removed.", false) }.onFailure { notify(it.message ?: "Could not revoke.", true) } } }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Revoke", color = Bento.danger) }
                    }
                }
            }
        }
        if (records.isNotEmpty()) item { Text("CONTACTS & CARDS", style = MaterialTheme.typography.labelSmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 10.dp)) }
        items(records, key = { it.shareId }) { r ->
            val person = if (tab == 0) r.owner else r.recipient
            val isContact = r.resourceType == "contact"
            val title = if (isContact) r.contact?.name.orEmpty() else r.card?.fullName.orEmpty()
            BentoCard(padding = 12.dp, onClick = { if (isContact) Details.openContact(r.resourceId) else r.card?.cardId?.let { nav.navigate(Routes.publicCard(it)) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIconBox(if (isContact) Icons.Outlined.ContactPage else Icons.Outlined.CreditCard, if (isContact) Tones.Blue else Tones.Violet, size = 40.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title.ifBlank { if (isContact) "Contact" else "Business card" }, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (tab == 0) "From" else "With"} ${person.fullName} · ${Dates.formatRelative(r.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                    }
                    if (tab == 1) TextButton(onClick = { scope.launch { runCatchingSafe { api.revokeRecordShare(r.shareId); vault.refreshShares() }.onSuccess { notify("Access removed.", false) }.onFailure { notify(it.message ?: "Could not revoke.", true) } } }) { Text("Revoke", color = Bento.danger) }
                }
            }
        }
    }
    comments?.let { CommentsSheet(it, user, onDismiss = { comments = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(share: SharedVaultEntry, user: AppUser, onDismiss: () -> Unit) {
    val api = LocalContext.current.appContainer.api
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ShareComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var body by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(share.shareId) { runCatchingSafe { api.loadShareComments(share.shareId) }.onSuccess { list = it }.onFailure { notify(it.message ?: "Could not load comments.", true) }; loading = false }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Bento.card, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).fillMaxHeight(0.8f)) {
            Eyebrow("Comments"); Text(share.item.title, style = MaterialTheme.typography.titleLarge, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            if (loading) LoadingBlock(Modifier.weight(1f)) else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.isEmpty()) item { Text("No comments yet. Start the conversation.", style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg) }
                items(list, key = { it.id }) { c ->
                    val mine = c.authorId == user.id
                    Row(verticalAlignment = Alignment.Top) {
                        Avatar(c.authorName.take(2).uppercase(), 30.dp); Spacer(Modifier.width(10.dp))
                        Column { Row { Text(if (mine) "You" else c.authorName, style = MaterialTheme.typography.labelLarge, color = Bento.fg); Spacer(Modifier.width(6.dp)); Text(Dates.formatRelative(c.createdAt), style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg) }; Text(c.body, style = MaterialTheme.typography.bodyMedium, color = Bento.fg) }
                    }
                }
            }
            if (share.permission != "view" || share.direction == "outgoing") Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(body, { body = it }, Modifier.weight(1f), placeholder = { Text("Write a comment…", color = Bento.subtleFg) }, shape = RoundedCornerShape(14.dp), colors = persoraFieldColors(), maxLines = 4)
                IconButton(onClick = { if (body.isNotBlank() && !sending) { sending = true; scope.launch { runCatchingSafe { api.addShareComment(share.shareId, body.trim()) }.onSuccess { list = list + it; body = "" }.onFailure { notify(it.message ?: "Could not post.", true) }; sending = false } } }, enabled = body.isNotBlank()) { Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = Bento.primary) }
            }
        }
    }
}
