package app.persora.android.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Cards
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import app.persora.android.core.util.PickedFile
import app.persora.android.data.model.*
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val WALLET_KEYS = setOf("network", "cardType", "issuer", "cardholder", "lastFour", "expiryMonth", "expiryYear", "currency", "notes")
private const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024

/**
 * ItemEditorDialog from VaultDialogs.tsx: builds the form from SECTION_DEFINITIONS, handles the four notes record
 * types, masks card numbers for wallet-cards, uploads attachments to R2 through /api/upload and runs Smart Scan.
 */
@Composable
fun ItemEditorScreen(sectionId: String, itemId: String?, folderId: String?, kind: String?, shareId: String?) {
    if (sectionId == "notes" && shareId == null) { NotesEditorScreen(itemId, folderId, kind); return }
    val section = Sections[sectionId]
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val api = container.api
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val items by vault.items.collectAsStateWithLifecycle()
    val incoming by vault.incomingShares.collectAsStateWithLifecycle()
    val foldersMap by vault.folders.collectAsStateWithLifecycle()
    val ringtones by vault.ringtones.collectAsStateWithLifecycle()
    val existing = remember(items, incoming) { items.firstOrNull { it.id == itemId } ?: incoming.firstOrNull { it.item.id == itemId }?.item }
    val isNew = existing == null

    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var subtitle by rememberSaveable { mutableStateOf(existing?.subtitle.orEmpty()) }
    var metadata by remember { mutableStateOf(existing?.metadata ?: buildMap { if (sectionId == "notes") put("recordType", kind ?: "note"); if (sectionId == "notes" && (kind == "reminder" || kind == "alarm")) { put("enabled", "true"); put("ringtoneId", BUILTIN_RINGTONES.first().id); put("ringtoneName", BUILTIN_RINGTONES.first().name) } }) }
    var favorite by rememberSaveable { mutableStateOf(existing?.favorite ?: false) }
    var folder by remember { mutableStateOf(existing?.folderId ?: folderId) }
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf(existing?.metadata?.let { m -> m["expiryMonth"]?.takeIf { it.isNotBlank() }?.let { "$it/${m["expiryYear"].orEmpty().takeLast(2)}" } }.orEmpty()) }
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var removeFile by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<SmartScanResult?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val recordType = metadata["recordType"] ?: ""
    val isWallet = sectionId == "wallet-cards"

    LaunchedEffect(sectionId) { vault.refreshFolders(sectionId); if (sectionId == "notes") vault.refreshRingtones() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatchingSafe { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val f = Files.describe(context, uri)
        if (f.size > MAX_UPLOAD_BYTES) notify("Files must be 25 MB or smaller.", true) else { picked = f; removeFile = false }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { picked = Files.describe(context, it).copy(name = "scan-${System.currentTimeMillis()}.jpg", mime = "image/jpeg"); removeFile = false } }

    fun set(key: String, value: String) { metadata = metadata + (key to value); if (errors.containsKey(key)) errors = errors - key }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (title.isBlank()) e["title"] = "Required"
        if (title.length > 240) e["title"] = "Keep it under 240 characters"
        section.fields.filter { it.key != "title" && it.required && !(sectionId == "notes" && it.key !in visibleNoteKeys(recordType)) }.forEach { f -> if (metadata[f.key].isNullOrBlank()) e[f.key] = "Required" }
        if (isWallet) {
            val cardDigits = cardNumber.filter { it.isDigit() }
            if (isNew && cardDigits.length !in 12..19) e["cardNumber"] = "Enter the full card number (12–19 digits); only the last four are stored"
            else if (cardDigits.isNotEmpty() && (cardDigits.length !in 12..19 || !Cards.passesLuhn(cardDigits))) e["cardNumber"] = "That doesn't look like a valid card number — please check it"
            if (Cards.splitExpiry(expiry) == null) e["expiry"] = "Use MM/YY"
        }
        if (recordType == "reminder" && metadata["reminderAt"].isNullOrBlank()) e["reminderAt"] = "Pick a date and time"
        if (recordType == "alarm") { if (metadata["alarmTime"].isNullOrBlank()) e["alarmTime"] = "Pick a time"; if (metadata["repeatDays"].isNullOrBlank() && metadata["alarmDate"].isNullOrBlank()) e["alarmDate"] = "Choose a date or repeat days" }
        errors = e; return e.isEmpty()
    }

    fun save() {
        if (!validate()) { notify("Please check the highlighted fields.", true); return }
        saving = true
        scope.launch {
            try {
                var meta = metadata.filterValues { it.isNotBlank() }.toMutableMap()
                if (isWallet) {
                    if (cardNumber.isNotBlank()) { meta["lastFour"] = Cards.lastFour(cardNumber); if (meta["network"].isNullOrBlank() || meta["network"] == "Other") meta["network"] = Cards.detectNetwork(cardNumber) }
                    Cards.splitExpiry(expiry)?.let { (m, y) -> meta["expiryMonth"] = m; meta["expiryYear"] = y }
                    meta["cardholder"] = title.trim()
                    meta = meta.filterKeys { it in WALLET_KEYS }.toMutableMap()
                }
                if (sectionId == "notes") { val keep = visibleNoteKeys(recordType) + setOf("recordType", "enabled", "ringtoneId", "ringtoneName", "snoozedUntil", "completed"); meta = meta.filterKeys { it in keep }.toMutableMap(); if (recordType == "todo" && meta["completed"] == null) meta["completed"] = "false" }
                var file: VaultFile? = if (removeFile) null else existing?.file
                val p = picked
                if (p != null) {
                    progress = 0
                    val uploaded = withContext(Dispatchers.IO) {
                        if (shareId != null) api.uploadSharedFile(shareId, p.name, p.mime, p.size) { p.open(context) }
                        else api.uploadVaultFile(p.name, p.mime, p.size, { p.open(context) }) { loaded, total -> progress = if (total > 0) ((loaded * 100) / total).toInt() else null }
                    }
                    file = uploaded; progress = null
                }
                val draft = VaultItem(id = existing?.id ?: UUID.randomUUID().toString(), section = sectionId, title = title.trim(), subtitle = subtitle.trim().ifBlank { null }, metadata = meta, createdAt = existing?.createdAt ?: Dates.nowIso(), updatedAt = Dates.nowIso(), file = file, favorite = favorite, pinned = existing?.pinned ?: false, folderId = folder)
                if (shareId != null) { withContext(Dispatchers.IO) { api.saveSharedDocument(shareId, draft) }; vault.refreshShares() } else vault.saveItem(draft)
                val old = existing?.file?.key
                if (shareId == null && old != null && (removeFile || p != null) && old != file?.key) runCatchingSafe { api.deleteVaultFile(old) }
                notify(if (isNew) "${section.singular.replaceFirstChar { it.uppercase() }} saved." else "Changes saved.", false)
                nav.popBackStack()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Could not save.", true) } finally { saving = false; progress = null }
        }
    }

    fun runScan(retry: Boolean) {
        val p = picked ?: return
        scanning = true
        scope.launch {
            try {
                val defs = section.fields.map { SmartScanFieldDefinition(it.key, it.label, it.kind.name.lowercase(), it.options.takeIf { o -> o.isNotEmpty() }) }
                scan = withContext(Dispatchers.IO) { api.smartScan(p.name, p.mime, p.size, { p.open(context) }, sectionId, defs, retry) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Smart scan failed.", true) } finally { scanning = false }
        }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.Close, "Cancel", tint = Bento.mutedFg) }
            Column(Modifier.weight(1f)) { Eyebrow(section.eyebrow); Text(if (isNew) "New ${noteKindLabel(sectionId, recordType, section.singular)}" else "Edit ${noteKindLabel(sectionId, recordType, section.singular)}", style = MaterialTheme.typography.titleMedium, color = Bento.fg) }
            PrimaryButton(if (progress != null) "Uploading ${progress}%" else if (saving) "Saving…" else "Save", onClick = ::save, enabled = !saving, loading = saving)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sectionId == "notes" && isNew) SegmentedTabs(listOf("Note", "Task", "Reminder", "Alarm"), listOf("note", "todo", "reminder", "alarm").indexOf(recordType).coerceAtLeast(0), { i -> val k = listOf("note", "todo", "reminder", "alarm")[i]; metadata = metadata + ("recordType" to k) + (if (k == "reminder" || k == "alarm") mapOf("enabled" to (metadata["enabled"] ?: "true"), "ringtoneId" to (metadata["ringtoneId"] ?: BUILTIN_RINGTONES.first().id), "ringtoneName" to (metadata["ringtoneName"] ?: BUILTIN_RINGTONES.first().name)) else emptyMap()) })

            if (section.fields.any { it.kind != FieldKind.TEXTAREA } && sectionId !in setOf("notes", "wallet-cards", "accounts", "personal-finance", "urls")) {
                BentoCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIconBox(Icons.Outlined.DocumentScanner, Tones.Violet, size = 36.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text("Smart scan", style = MaterialTheme.typography.titleSmall, color = Bento.fg); Text(if (picked == null) "Attach a photo or PDF, then let Persora fill the fields." else "Read ${picked!!.name} and suggest values.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        if (picked != null) SoftButton(if (scanning) "Scanning…" else "Scan", onClick = { if (!scanning) runScan(false) }, icon = Icons.Outlined.AutoAwesome)
                    }
                    scan?.let { result ->
                        Spacer(Modifier.height(10.dp))
                        if (result.documentType.isNotBlank()) Pill("Looks like: ${result.documentType} · ${result.documentTypeConfidence}", Tones.Violet)
                        result.fields.filter { it.value.value.isNotBlank() }.forEach { (key, r) ->
                            val label = section.fields.firstOrNull { it.key == key }?.label ?: key
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg); Text(r.value, style = MaterialTheme.typography.bodyMedium, color = Bento.fg, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Pill(r.confidence, if (r.confidence == "high") Tones.Green else if (r.confidence == "medium") Tones.Amber else Tones.Neutral)
                                TextButton(onClick = { if (key == "title") title = r.value else set(key, r.value) }) { Text("Use", color = Bento.primary) }
                            }
                        }
                        result.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Accents.amber.text) }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuietButton("Apply all", onClick = { result.fields.forEach { (k, r) -> if (r.value.isNotBlank()) { if (k == "title") title = r.value else set(k, r.value) } } })
                            TextButton(onClick = { runScan(true) }) { Text("Re-scan", color = Bento.mutedFg) }
                        }
                    }
                }
            }

            BentoCard {
                TextInput(title, { title = it; errors = errors - "title" }, if (isWallet) "Cardholder name" else section.titleLabel, required = true, isError = errors["title"] != null, supporting = errors["title"])
                if (sectionId !in setOf("notes", "wallet-cards")) { Spacer(Modifier.height(10.dp)); TextInput(subtitle, { subtitle = it }, "Short subtitle", placeholder = "Optional one-line summary") }

                if (isWallet) {
                    Spacer(Modifier.height(10.dp))
                    val detected = Cards.detectNetwork(cardNumber)
                    TextInput(
                        Cards.formatNumber(cardNumber),
                        { v -> val d = Cards.digits(v); cardNumber = d; errors = errors - "cardNumber"; val net = Cards.detectNetwork(d); if (net.isNotBlank()) set("network", net) else if (isNew) set("network", "") },
                        if (isNew) "Card number" else "Card number (leave blank to keep •••• ${existing?.metadata?.get("lastFour")})", keyboard = KeyboardType.Number,
                        supporting = errors["cardNumber"] ?: if (detected.isNotBlank()) "$detected detected · only the brand and last four digits are stored." else "Only the brand and last four digits are stored — never the full number or CVV.", isError = errors["cardNumber"] != null,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextInput(expiry, { expiry = Cards.formatExpiry(it); errors = errors - "expiry" }, "Expiry (MM/YY)", modifier = Modifier.weight(1f), keyboard = KeyboardType.Number, isError = errors["expiry"] != null, supporting = errors["expiry"], placeholder = "MM/YY")
                        SelectInput(metadata["network"].orEmpty(), Cards.networks, { set("network", it) }, "Network", modifier = Modifier.weight(1f), allowEmpty = false)
                    }
                }

                val visibleFields = section.fields.filter { f -> f.key !in setOf("title", "lastFour", "expiryMonth", "expiryYear", "cardholder", "network", "cardNumber", "expiry") && (sectionId != "notes" || f.key in visibleNoteKeys(recordType)) }
                if (sectionId == "notes" && recordType != "note") { Spacer(Modifier.height(10.dp)); TextInput(metadata["todoDetails"].orEmpty(), { set("todoDetails", it) }, "Details", placeholder = "Anything you want to remember", minLines = 3) }
                if (recordType == "todo") { Spacer(Modifier.height(10.dp)); DateInput(metadata["dueDate"].orEmpty(), { set("dueDate", it) }, "Due date") }
                visibleFields.forEach { f ->
                    Spacer(Modifier.height(10.dp))
                    val v = metadata[f.key].orEmpty()
                    val err = errors[f.key]
                    when (f.kind) {
                        FieldKind.SELECT -> SelectInput(v, f.options, { set(f.key, it) }, f.label, required = f.required)
                        FieldKind.DATE -> DateInput(v, { set(f.key, it) }, f.label, required = f.required)
                        FieldKind.TEXTAREA -> TextInput(v, { set(f.key, it) }, f.label, placeholder = f.placeholder, minLines = 4, required = f.required, isError = err != null, supporting = err)
                        FieldKind.NUMBER -> TextInput(v, { set(f.key, it) }, f.label, placeholder = f.placeholder, keyboard = KeyboardType.Decimal, required = f.required, isError = err != null, supporting = err)
                        FieldKind.EMAIL -> TextInput(v, { set(f.key, it) }, f.label, placeholder = f.placeholder, keyboard = KeyboardType.Email, required = f.required, isError = err != null, supporting = err)
                        FieldKind.URL -> TextInput(v, { set(f.key, it) }, f.label, placeholder = f.placeholder, keyboard = KeyboardType.Uri, required = f.required, isError = err != null, supporting = err)
                        else -> TextInput(v, { set(f.key, it) }, f.label, placeholder = f.placeholder, required = f.required, isError = err != null, supporting = err)
                    }
                }

                if (recordType == "reminder") { Spacer(Modifier.height(10.dp)); DateTimeInput(metadata["reminderAt"].orEmpty(), { set("reminderAt", it) }, "Remind me at", required = true); errors["reminderAt"]?.let { Text(it, color = Bento.danger, style = MaterialTheme.typography.bodySmall) } }
                if (recordType == "alarm") {
                    Spacer(Modifier.height(10.dp)); TimeInput(metadata["alarmTime"].orEmpty(), { set("alarmTime", it) }, "Alarm time", required = true); errors["alarmTime"]?.let { Text(it, color = Bento.danger, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(10.dp)); Text("Repeat", style = MaterialTheme.typography.labelLarge, color = Bento.fg)
                    val selected = metadata["repeatDays"].orEmpty().split(",").mapNotNull { it.toIntOrNull() }.toSet()
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dates.weekdayLabels.forEachIndexed { i, d ->
                            val on = i in selected
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (on) Bento.primary else Bento.card).border(1.dp, if (on) Bento.primary else Bento.borderStrong, CircleShape).clickable { val next = if (on) selected - i else selected + i; set("repeatDays", next.sorted().joinToString(",")); if (next.isNotEmpty()) set("alarmDate", "") }, contentAlignment = Alignment.Center) { Text(d.take(1), style = MaterialTheme.typography.labelLarge, color = if (on) Bento.primaryFg else Bento.fg) }
                        }
                    }
                    if (selected.isEmpty()) { Spacer(Modifier.height(10.dp)); DateInput(metadata["alarmDate"].orEmpty(), { set("alarmDate", it) }, "One-time date", required = true) }
                    errors["alarmDate"]?.let { Text(it, color = Bento.danger, style = MaterialTheme.typography.bodySmall) }
                }
                if (recordType == "reminder" || recordType == "alarm") {
                    Spacer(Modifier.height(10.dp))
                    val options = BUILTIN_RINGTONES.map { it.id to it.name } + ringtones.map { it.id to it.name }
                    SelectInput(options.firstOrNull { it.first == metadata["ringtoneId"] }?.second ?: options.first().second, options.map { it.second }, { name -> options.firstOrNull { it.second == name }?.let { set("ringtoneId", it.first); set("ringtoneName", it.second) } }, "Ringtone", allowEmpty = false)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) { Switch(checked = metadata["enabled"] != "false", onCheckedChange = { set("enabled", it.toString()) }, colors = SwitchDefaults.colors(checkedTrackColor = Bento.primary)); Spacer(Modifier.width(8.dp)); Text(if (metadata["enabled"] != "false") "Active — will ring on this phone and the website" else "Paused", style = MaterialTheme.typography.bodyMedium, color = Bento.fg) }
                }
            }

            if (sectionId != "wallet-cards" && sectionId != "notes" || recordType == "note") BentoCard {
                SectionHeading("Attachment", if (existing?.file != null && !removeFile && picked == null) "Current file" else "Add a file")
                val current = picked?.let { it.name to Files.formatSize(it.size) } ?: existing?.file?.takeIf { !removeFile }?.let { it.name to Files.formatSize(it.size) }
                if (current != null) Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToneIconBox(Icons.Outlined.AttachFile, Tones.Blue, size = 34.dp, radius = 10.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(current.first, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(current.second, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
                    IconButton(onClick = { if (picked != null) picked = null else removeFile = true }) { Icon(Icons.Outlined.Delete, "Remove", tint = Bento.danger) }
                } else Text("PDFs, images and documents up to 25 MB are stored privately in your vault.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Choose file", onClick = { filePicker.launch(arrayOf("*/*")) }, icon = Icons.Outlined.UploadFile)
                    QuietButton("Take photo", onClick = { val uri = Files.newCameraUri(context); cameraUri = uri; camera.launch(uri) }, icon = Icons.Outlined.PhotoCamera)
                }
            }

            BentoCard {
                SectionHeading("Organize", "Folder & favorite")
                val folders = foldersMap[sectionId].orEmpty()
                if (folders.isNotEmpty()) SelectInput(folders.firstOrNull { it.id == folder }?.name.orEmpty(), folders.map { it.name }, { name -> folder = folders.firstOrNull { it.name == name }?.id }, "Folder")
                else Text("Create folders from the ${section.label} page to group records.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Switch(checked = favorite, onCheckedChange = { favorite = it }, colors = SwitchDefaults.colors(checkedTrackColor = Bento.primary)); Spacer(Modifier.width(8.dp)); Text("Mark as favorite", style = MaterialTheme.typography.bodyMedium) }
            }
            Text("Saved to the same Persora vault you use on the web. Section: ${section.label}.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
        }
    }
}

private fun noteKindLabel(sectionId: String, recordType: String, fallback: String) = if (sectionId != "notes") fallback else when (recordType) { "todo" -> "task"; "reminder" -> "reminder"; "alarm" -> "alarm"; else -> "note" }

/** Which section-field keys apply to each notes record type (mirrors the conditional form in VaultDialogs.tsx). */
private fun visibleNoteKeys(recordType: String): Set<String> = when (recordType) {
    "todo" -> setOf("todoDetails", "dueDate", "tags")
    "reminder" -> setOf("todoDetails", "reminderAt", "tags")
    "alarm" -> setOf("todoDetails", "alarmTime", "alarmDate", "repeatDays", "tags")
    else -> setOf("content", "tags", "category")
}
