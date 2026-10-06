package app.persora.android.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.ContactImport
import app.persora.android.core.util.ContactImport.Entry
import app.persora.android.ui.components.*
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.MonoCaption
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Step {
    data object Pick : Step
    data class Reading(val label: String) : Step
    data class Preview(val entries: List<Entry>, val source: String) : Step
    data class Importing(val total: Int, val done: Int, val current: String) : Step
    data class Done(val summary: ContactImport.Summary) : Step
    data class Failed(val message: String) : Step
}

/**
 * "Import contacts" drawer: vCard / CSV upload with the website's preview (duplicates flagged, invalid numbers
 * skipped, select what to import), or a one-tap sync from the phone's address book that uploads only the
 * people whose numbers aren't in Persora yet.
 */
@Composable
fun ContactImportSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val api = container.api
    val notify = app.persora.android.ui.navigation.LocalNotify.current
    val scope = rememberCoroutineScope()
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf<Step>(Step.Pick) }
    val busy = step is Step.Reading || step is Step.Importing

    // Runs entirely on IO: uploads go straight through the API and the local contact list / cache is refreshed once at
    // the end (saving through the repository per contact re-serialised the whole list on every row and stalled the UI).
    suspend fun runImport(entries: List<Entry>): ContactImport.Summary = withContext(Dispatchers.IO) {
        val chosen = entries.filter { it.selected }
        val failed = ArrayList<Pair<String, String>>()
        var imported = 0
        chosen.forEachIndexed { index, e ->
            step = Step.Importing(chosen.size, index, e.name)
            try {
                var photoKey: String? = null
                e.draft.photo?.let { bytes ->
                    runCatching { api.uploadVaultFile("${e.name.take(40).replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "contact" }}-photo.jpg", e.draft.photoMime, bytes.size.toLong(), { bytes.inputStream() }) }.onSuccess { photoKey = it.key }
                }
                api.saveContact(e.draft.contact.copy(photoKey = photoKey), isNew = true)
                imported++
            } catch (ex: kotlinx.coroutines.CancellationException) { throw ex } catch (ex: Exception) {
                failed += e.name to humanizeError(ex.message ?: "Couldn't save.", "error").first
            }
        }
        if (imported > 0) runCatching { vault.refreshContacts() }
        ContactImport.Summary(
            imported = imported,
            skippedDuplicates = entries.count { !it.selected && it.duplicateOf.isNotBlank() },
            skippedInvalidNumbers = entries.sumOf { it.invalidPhones.size },
            skippedDuplicateNumbers = entries.sumOf { it.duplicatePhones.size },
            failed = failed,
        )
    }

    fun importFile(uri: Uri) {
        step = Step.Reading("Reading file…")
        scope.launch {
            try {
                val (name, text) = withContext(Dispatchers.IO) {
                    val n = runCatching { context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) { val size = c.getLong(1); if (size > 15L * 1024 * 1024) error("Files must be smaller than 15 MB."); c.getString(0) } else null } }.getOrNull()
                    val t = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { bytes -> if (bytes.size > 15 * 1024 * 1024) error("Files must be smaller than 15 MB."); String(bytes, Charsets.UTF_8) } ?: error("Couldn't read that file.")
                    n to t
                }
                val drafts = withContext(Dispatchers.Default) { ContactImport.parseAny(text, name) }
                if (drafts.isEmpty()) error(if (name?.lowercase()?.endsWith(".csv") == true) "No contacts were found in that CSV. Make sure it has a header row with name, phone or e-mail columns." else "No readable contacts were found in that file. Export as vCard (.vcf) or CSV and try again.")
                val prepared = withContext(Dispatchers.Default) { ContactImport.prepare(drafts, contacts) }
                step = Step.Preview(prepared, name ?: "file")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { step = Step.Failed(e.message ?: "Couldn't read that file.") }
        }
    }

    fun importFromPhone() {
        step = Step.Reading("Checking your phone contacts…")
        scope.launch {
            try {
                val drafts = withContext(Dispatchers.IO) { ContactImport.readPhoneContacts(context) }
                if (drafts.isEmpty()) { step = Step.Failed("No contacts with a phone number or e-mail were found on this phone."); return@launch }
                val entries = withContext(Dispatchers.Default) { ContactImport.prepare(drafts, contacts) }
                val toImport = entries.filter { it.selected }
                if (toImport.isEmpty()) { step = Step.Done(ContactImport.Summary(0, entries.count { it.duplicateOf.isNotBlank() }, entries.sumOf { it.invalidPhones.size }, entries.sumOf { it.duplicatePhones.size }, emptyList())); return@launch }
                val summary = runImport(entries)
                step = Step.Done(summary)
                if (summary.imported > 0) notify("${summary.imported} contact${if (summary.imported == 1) "" else "s"} imported from your phone.", false)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { step = Step.Failed(humanizeError(e.message ?: "Couldn't read your phone contacts.", "error").first) }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importFile(uri) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) importFromPhone() else step = Step.Failed("Persora needs access to your contacts to find the ones that aren't saved yet. You can allow it in Settings → Apps → Persora → Permissions.") }

    DetailSheet(onDismiss = { if (!busy) onDismiss() }, heightFraction = 0.92f) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                Column(Modifier.weight(1f)) { MonoLabel("CONTACTS"); Text("Import contacts", style = MaterialTheme.typography.headlineSmall, color = Bento.fg) }
                if (!busy) TextButton(onClick = onDismiss) { Text("Close", color = Bento.mutedFg) }
            }
            when (val s = step) {
                Step.Pick -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceTile(Icons.Outlined.UploadFile, "Upload a file", "vCard (.vcf) or CSV exported from Google Contacts, iCloud, Outlook or another phone. You'll review what gets imported.", "VCF · CSV") {
                        filePicker.launch(arrayOf("text/vcard", "text/x-vcard", "text/directory", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "text/plain", "application/octet-stream"))
                    }
                    SourceTile(Icons.Outlined.ContactPhone, "From phone contacts", "Checks every contact on this phone and uploads only the people whose numbers aren't in Persora yet. Numbers that can't be read are skipped automatically.", "AUTO") {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) importFromPhone() else permission.launch(Manifest.permission.READ_CONTACTS)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Same rules as the website: invalid numbers are dropped, numbers already saved on another contact are filtered out, and people who already exist are flagged as duplicates instead of being added twice.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
                }
                is Step.Reading -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Bento.primary); Spacer(Modifier.height(14.dp)); Text(s.label, style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg) }
                is Step.Preview -> PreviewList(s.entries, s.source, onToggle = { idx -> step = Step.Preview(s.entries.mapIndexed { i, e -> if (i == idx) e.copy(selected = !e.selected) else e }, s.source) }, onAll = { on -> step = Step.Preview(s.entries.map { it.copy(selected = on && it.hasUsableData) }, s.source) }, onBack = { step = Step.Pick }) {
                    scope.launch { try { step = Step.Done(runImport(s.entries)) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { step = Step.Failed(humanizeError(e.message ?: "The contacts couldn't be imported.", "error").first) } }
                }
                is Step.Importing -> Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.done} / ${s.total}", style = app.persora.android.ui.theme.MonoStat, color = Bento.fg)
                    Spacer(Modifier.height(8.dp))
                    ProgressTrack(if (s.total == 0) 0f else s.done.toFloat() / s.total, app.persora.android.ui.theme.Accents.brand, modifier = Modifier.fillMaxWidth(0.7f), shimmer = true)
                    Spacer(Modifier.height(10.dp))
                    Text("Importing ${s.current}…", style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Keep this open until it finishes.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
                }
                is Step.Done -> SummaryView(s.summary, onClose = onDismiss, onMore = { step = Step.Pick })
                is Step.Failed -> Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ToneIconBox(Icons.Outlined.ErrorOutline, Tones.Red, size = 44.dp); Spacer(Modifier.height(10.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = Bento.fg, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(14.dp)); QuietButton("Try another way", onClick = { step = Step.Pick })
                }
            }
        }
    }
}

@Composable
private fun SourceTile(icon: ImageVector, title: String, body: String, tag: String, onClick: () -> Unit) {
    BentoCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIconBox(icon, Tones.Blue, size = 44.dp, radius = 12.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleSmall, color = Bento.fg); Spacer(Modifier.width(8.dp)); Text(tag, style = MonoCaption, color = Bento.mutedFg) }
                Text(body, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
            }
        }
    }
}

@Composable
private fun PreviewList(entries: List<Entry>, source: String, onToggle: (Int) -> Unit, onAll: (Boolean) -> Unit, onBack: () -> Unit, onImport: () -> Unit) {
    val chosen = entries.count { it.selected }
    val dupes = entries.count { it.duplicateOf.isNotBlank() }
    val invalid = entries.sumOf { it.invalidPhones.size }
    val dupNumbers = entries.sumOf { it.duplicatePhones.size }
    Column(Modifier.fillMaxSize()) {
        Text("${entries.size} contact${if (entries.size == 1) "" else "s"} found in $source", style = MaterialTheme.typography.bodyMedium, color = Bento.fg)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dupes > 0) Pill("$dupes duplicate${if (dupes == 1) "" else "s"}", Tones.Amber)
            if (invalid > 0) Pill("$invalid invalid number${if (invalid == 1) "" else "s"}", Tones.Red)
            if (dupNumbers > 0) Pill("$dupNumbers repeated number${if (dupNumbers == 1) "" else "s"}", Tones.Neutral)
        }
        Row(Modifier.padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$chosen selected", style = MonoCaption, color = Bento.mutedFg, modifier = Modifier.weight(1f))
            TextButton(onClick = { onAll(true) }) { Text("Select all") }; TextButton(onClick = { onAll(false) }) { Text("None") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries.indices.toList()) { i ->
                val e = entries[i]
                val c = e.draft.contact
                val shape = RoundedCornerShape(14.dp)
                Row(Modifier.fillMaxWidth().clip(shape).background(if (e.selected) Bento.primarySoft else Bento.card).border(1.dp, if (e.selected) Bento.primary.copy(alpha = 0.5f) else Bento.border, shape).clickable(enabled = e.hasUsableData) { onToggle(i) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(e.selected, onCheckedChange = { onToggle(i) }, enabled = e.hasUsableData, colors = CheckboxDefaults.colors(checkedColor = Bento.primary))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(c.phoneNumbers.firstOrNull()?.number, c.email.takeIf { it.isNotBlank() }, c.company.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ").ifBlank { "No usable number or e-mail" }, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.duplicateOf.isNotBlank()) Text("Looks like ${e.duplicateOf}, already in Persora", style = MaterialTheme.typography.labelSmall, color = Tones.Amber.fg)
                        if (e.invalidPhones.isNotEmpty()) Text("Skipping invalid: ${e.invalidPhones.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = Tones.Red.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.duplicatePhones.isNotEmpty()) Text("Already saved: ${e.duplicatePhones.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = Bento.subtleFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (c.phoneNumbers.size > 1) Text("${c.phoneNumbers.size} nos.", style = MonoCaption, color = Bento.subtleFg)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("Back", onClick = onBack)
            PrimaryButton(if (chosen == 0) "Select contacts" else "Import $chosen contact${if (chosen == 1) "" else "s"}", onClick = onImport, enabled = chosen > 0, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryView(s: ContactImport.Summary, onClose: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIconBox(if (s.failed.isEmpty()) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, if (s.failed.isEmpty()) Tones.Green else Tones.Amber, size = 44.dp); Spacer(Modifier.width(12.dp))
            Column { Text(if (s.imported > 0) "${s.imported} contact${if (s.imported == 1) "" else "s"} imported" else "Nothing new to import", style = MaterialTheme.typography.titleMedium, color = Bento.fg); Text(if (s.imported > 0) "They're in your Persora vault on every device now." else "Everyone we found is already saved in Persora.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
        }
        Spacer(Modifier.height(14.dp))
        BentoCard(padding = 12.dp) {
            StatRow("Already in Persora (skipped)", s.skippedDuplicates)
            StatRow("Invalid numbers skipped", s.skippedInvalidNumbers)
            StatRow("Repeated numbers filtered", s.skippedDuplicateNumbers)
            StatRow("Failed to save", s.failed.size)
        }
        if (s.failed.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            BentoCard(padding = 12.dp) { s.failed.take(8).forEach { (n, why) -> Text("$n — $why", style = MaterialTheme.typography.bodySmall, color = Tones.Red.fg, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuietButton("Import more", onClick = onMore); PrimaryButton("Done", onClick = onClose, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Bento.fg)
    }
}
