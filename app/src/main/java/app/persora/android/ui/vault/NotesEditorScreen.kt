package app.persora.android.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import app.persora.android.core.util.PickedFile
import app.persora.android.core.util.RingtonePreview
import app.persora.android.core.util.runCatchingSafe
import app.persora.android.data.model.*
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.EditorDrawer
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.NoteColors
import app.persora.android.ui.theme.Tone
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Same ids/order as WEEKDAYS in VaultDialogs.tsx (Sunday = "0"). */
private val WEEKDAYS = listOf("0" to "Sun", "1" to "Mon", "2" to "Tue", "3" to "Wed", "4" to "Thu", "5" to "Fri", "6" to "Sat")
private const val MAX_DETAILS = 5000
private const val MAX_NOTE_UPLOAD_BYTES = 25L * 1024 * 1024
private const val MAX_NOTE_SMART_SCAN_BYTES = 7L * 1024 * 1024

private fun supportsNoteSmartScan(mime: String, name: String, size: Long): Boolean {
    val type = mime.lowercase().substringBefore(';')
    val ext = name.substringAfterLast('.', "").lowercase()
    return size in 1L..MAX_NOTE_SMART_SCAN_BYTES && (type in setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "image/gif", "image/tiff", "image/bmp") || ext in setOf("pdf", "jpg", "jpeg", "png", "webp", "gif", "tif", "tiff", "bmp"))
}

private fun noteSmartScanFields(recordType: String): List<SmartScanFieldDefinition> = buildList {
    add(SmartScanFieldDefinition("title", "Title", "text"))
    when (recordType) {
        "note" -> { add(SmartScanFieldDefinition("content", "Note", "textarea")); add(SmartScanFieldDefinition("tags", "Tags", "text")) }
        "todo" -> { add(SmartScanFieldDefinition("todoDetails", "Details", "textarea")); add(SmartScanFieldDefinition("dueDate", "Due date", "date")) }
        else -> add(SmartScanFieldDefinition("todoDetails", "Details", "textarea"))
    }
    add(SmartScanFieldDefinition("additionalData", "Additional Data", "textarea"))
}

private data class NoteKind(val id: String, val label: String, val icon: ImageVector, val tone: Tone, val eyebrow: String, val headline: String, val intro: String, val titlePlaceholder: String)

private val KINDS = listOf(
    NoteKind("note", "Note", Icons.Outlined.StickyNote2, Tones.Amber, "Tasks & Notes", "Write a note.", "Ideas, lists, snippets — anything worth keeping.", "e.g. Ideas for the weekend"),
    NoteKind("todo", "Task", Icons.Outlined.CheckCircle, Tones.Green, "Tasks & Notes", "Add a task.", "Jot down what needs to happen and when.", "e.g. Send the application form"),
    NoteKind("reminder", "Reminder", Icons.Outlined.NotificationsActive, Tones.Blue, "Tasks & Notes", "Set a reminder.", "Persora will notify you at the exact date and time.", "e.g. Call the clinic"),
    NoteKind("alarm", "Alarm", Icons.Outlined.Alarm, Tones.Purple, "Tasks & Notes", "Set an alarm.", "Pick a time, then a single date or the days it should repeat.", "e.g. Morning alarm"),
)

/**
 * Port of TodoEditorDialog (tasks, reminders, alarms) and the notes ItemEditorDialog from VaultDialogs.tsx, designed for a
 * phone: kind picker, big time controls, weekday chips, ringtone preview, quick-pick dates. Metadata matches the web exactly.
 */
@Composable
fun NotesEditorScreen(itemId: String?, folderId: String?, kind: String?, initialMetadata: Map<String, String> = emptyMap(), initialFile: PickedFile? = null, initialScanResult: SmartScanResult? = null, initialScanComplete: Boolean = false, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val nav = LocalNav.current
    val closeEditor: () -> Unit = onClose ?: { nav.popBackStack() }
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val items by vault.items.collectAsStateWithLifecycle()
    val ringtones by vault.ringtones.collectAsStateWithLifecycle()
    val existing = remember(items) { items.firstOrNull { it.id == itemId } }
    val isNew = existing == null
    val m = existing?.metadata.orEmpty() + initialMetadata

    var recordType by rememberSaveable { mutableStateOf(m["recordType"]?.takeIf { it in listOf("todo", "reminder", "alarm") } ?: (if (existing != null) "note" else kind?.takeIf { it in listOf("note", "todo", "reminder", "alarm") } ?: "note")) }
    var title by rememberSaveable(existing?.id, kind, initialMetadata["title"]) { mutableStateOf(existing?.title ?: initialMetadata["title"].orEmpty()) }
    var content by rememberSaveable { mutableStateOf(m["content"].orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(m["tags"].orEmpty()) }
    var additionalData by rememberSaveable { mutableStateOf(m["additionalData"].orEmpty()) }
    var details by rememberSaveable { mutableStateOf(m["todoDetails"].orEmpty()) }
    var dueDate by rememberSaveable { mutableStateOf(m["dueDate"].orEmpty()) }
    var completed by rememberSaveable { mutableStateOf(m["completed"] == "true") }
    var reminderAt by rememberSaveable { mutableStateOf(m["reminderAt"].orEmpty()) }
    var alarmTime by rememberSaveable { mutableStateOf(m["alarmTime"]?.takeIf { it.isNotBlank() } ?: "08:00") }
    var alarmDate by rememberSaveable { mutableStateOf(m["alarmDate"].orEmpty()) }
    var repeatDays by rememberSaveable { mutableStateOf(m["repeatDays"].orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet().toList().sorted().joinToString(",")) }
    var enabled by rememberSaveable { mutableStateOf(m["enabled"] != "false") }
    var ringtoneId by rememberSaveable { mutableStateOf(m["ringtoneId"]?.takeIf { it.isNotBlank() } ?: BUILTIN_RINGTONES.first().id) }
    var color by rememberSaveable { mutableStateOf(m["color"].orEmpty()) }
    var paletteOpen by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf<String?>(null) }
    var picked by remember(initialFile) { mutableStateOf(initialFile) }
    var autoScannedUri by remember { mutableStateOf<Uri?>(null) }
    var removeFile by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf<Int?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanResult by remember(initialScanResult) { mutableStateOf(initialScanResult) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val canUpload = observeCurrentUser()?.uploadsEnabled == true
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        if (!canUpload) { notify("New attachments require an active paid plan.", true); return@rememberLauncherForActivityResult }
        val file = Files.describe(context, uri)
        if (file.size > MAX_NOTE_UPLOAD_BYTES) notify("Files must be 25 MB or smaller.", true)
        else { picked = file; removeFile = false; scanResult = null; scanError = null }
    }

    val kindDef = KINDS.first { it.id == recordType }
    val repeatSet = repeatDays.split(",").filter { it.isNotBlank() }.toSet()
    val ringtoneOptions = BUILTIN_RINGTONES.map { it.id to it.name } + ringtones.map { it.id to it.name }
    val ringtoneName = ringtoneOptions.firstOrNull { it.first == ringtoneId }?.second ?: BUILTIN_RINGTONES.first().name
    val scanFieldLabels = noteSmartScanFields(recordType).associate { it.key to it.label }
    val savedFileForEditor = existing?.file?.takeIf { !removeFile && picked == null }
    val canScanNoteFile = picked?.let { supportsNoteSmartScan(it.mime, it.name, it.size) }
        ?: savedFileForEditor?.let { it.key != null && supportsNoteSmartScan(it.type.orEmpty(), it.name, it.size ?: 0L) }
        ?: false

    LaunchedEffect(Unit) { vault.refreshRingtones() }
    DisposableEffect(Unit) { onDispose { RingtonePreview.stop() } }

    // Live preview of when the schedule will next ring, mirroring nextScheduleDate() on the web.
    val nextRing = remember(recordType, reminderAt, alarmTime, alarmDate, repeatDays, enabled) {
        if (!enabled) null else runCatching {
            val draftMeta = when (recordType) {
                "reminder" -> mapOf("recordType" to "reminder", "reminderAt" to reminderAt)
                "alarm" -> mapOf("recordType" to "alarm", "alarmTime" to alarmTime, "alarmDate" to alarmDate, "repeatDays" to repeatDays)
                else -> return@runCatching null
            }
            Dates.nextScheduleDate(VaultItem(id = "draft", section = "notes", title = "", metadata = draftMeta, createdAt = Dates.nowIso(), updatedAt = Dates.nowIso()))
        }.getOrNull()
    }

    fun runSmartScan(retry: Boolean = false) {
        if (!canUpload) { scanError = "Smart Scan uploads require an active paid plan."; return }
        val definitions = noteSmartScanFields(recordType)
        val file = picked
        val savedFile = existing?.file?.takeIf { !removeFile && it.key != null }
        if (file == null && savedFile == null) { scanError = "Attach a PDF or image to scan."; return }
        if (file != null && !supportsNoteSmartScan(file.mime, file.name, file.size)) { scanError = "Smart Scan supports PDF and common image files up to 7 MB."; return }
        scanning = true; scanError = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (file != null) container.api.smartScan(file.name, file.mime, file.size, { file.open(context) }, "notes", definitions, retry)
                    else {
                        val saved = requireNotNull(savedFile)
                        val bytes = container.api.openVaultFile(requireNotNull(saved.key)).use { it.stream.readBytes() }
                        if (!supportsNoteSmartScan(saved.type.orEmpty(), saved.name, bytes.size.toLong())) error("Smart Scan supports PDF and common image files up to 7 MB.")
                        container.api.smartScan(saved.name, saved.type.orEmpty(), bytes.size.toLong(), { bytes.inputStream() }, "notes", definitions, retry)
                    }
                }
                scanResult = result
                result.fields.forEach { (key, field) ->
                    if (field.value.isBlank() || definitions.none { it.key == key }) return@forEach
                    when (key) {
                        "title" -> title = field.value
                        "content" -> content = field.value.take(MAX_DETAILS)
                        "tags" -> tags = field.value.take(1000)
                        "todoDetails" -> details = field.value.take(MAX_DETAILS)
                        "dueDate" -> dueDate = field.value
                        "additionalData" -> additionalData = field.value.take(MAX_DETAILS)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { scanError = e.message ?: "Smart Scan failed. Please retry." }
            finally { scanning = false }
        }
    }

    LaunchedEffect(initialFile?.uri, picked?.uri, recordType, canUpload, initialScanComplete) {
        val initial = initialFile ?: return@LaunchedEffect
        if (picked?.uri != initial.uri || autoScannedUri == initial.uri) return@LaunchedEffect
        autoScannedUri = initial.uri
        if (!initialScanComplete && canUpload && supportsNoteSmartScan(initial.mime, initial.name, initial.size)) runSmartScan(false)
    }

    fun validate(): String? {
        if (title.isBlank()) return "Give it a title first."
        if (title.length > 240) return "Keep the title under 240 characters."
        if (details.length > MAX_DETAILS || content.length > MAX_DETAILS || additionalData.length > MAX_DETAILS) return "Keep the text under $MAX_DETAILS characters."
        val now = ZonedDateTime.now()
        when (recordType) {
            "reminder" -> {
                val at = Dates.parseInstant(reminderAt) ?: return "Choose a date and time for this reminder."
                if (isNew && !at.isAfter(now)) return "Choose a future date and time for this reminder."
            }
            "alarm" -> {
                if (alarmTime.isBlank() || (repeatSet.isEmpty() && alarmDate.isBlank())) return "Choose an alarm time and either a date or repeat days."
                if (repeatSet.isEmpty()) {
                    val at = runCatching { LocalDate.parse(alarmDate).atTime(LocalTime.parse(alarmTime)).atZone(ZoneId.systemDefault()) }.getOrNull() ?: return "Choose an alarm time and either a date or repeat days."
                    if (isNew && !at.isAfter(now)) return "Choose a future date and time for this alarm."
                }
            }
        }
        return null
    }

    val dirty = title != existing?.title.orEmpty() || content != m["content"].orEmpty() || tags != m["tags"].orEmpty() || additionalData != m["additionalData"].orEmpty() || color != m["color"].orEmpty() || picked != null || removeFile

    fun save(quiet: Boolean = false) {
        if (picked != null && !canUpload) { notify("New note attachments require an active paid plan.", true); return }
        validate()?.let { if (quiet && recordType == "note" && title.isBlank() && content.isNotBlank()) { title = content.lineSequence().first().take(60) } else { error = it; notify(it, true); return } }
        error = null; saving = true
        scope.launch {
            var uploadedKey = ""
            try {
                val meta = buildMap {
                    when (recordType) {
                        "note" -> { if (color.isNotBlank()) put("color", color); if (content.isNotBlank()) put("content", content.trim()); if (tags.isNotBlank()) put("tags", tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")) }
                        "todo" -> { put("recordType", "todo"); put("completed", completed.toString()); if (dueDate.isNotBlank()) put("dueDate", dueDate); if (details.isNotBlank()) put("todoDetails", details.trim()) }
                        "reminder" -> { put("recordType", "reminder"); put("enabled", enabled.toString()); put("ringtoneId", ringtoneId); put("ringtoneName", ringtoneName); put("reminderAt", Dates.parseInstant(reminderAt)!!.toInstant().toString()); if (details.isNotBlank()) put("todoDetails", details.trim()) }
                        "alarm" -> { put("recordType", "alarm"); put("enabled", enabled.toString()); put("ringtoneId", ringtoneId); put("ringtoneName", ringtoneName); put("alarmTime", alarmTime); if (repeatSet.isNotEmpty()) put("repeatDays", repeatSet.sorted().joinToString(",")) else put("alarmDate", alarmDate); if (details.isNotBlank()) put("todoDetails", details.trim()) }
                    }
                    if (additionalData.isNotBlank()) put("additionalData", additionalData.trim())
                    // Keep an active snooze only while the reminder is unchanged; a new time clears it, as on the web.
                    val snoozed = existing?.metadata?.get("snoozedUntil")
                    if (snoozed != null && recordType == "reminder" && existing.metadata["reminderAt"] == this["reminderAt"]) put("snoozedUntil", snoozed)
                }
                var finalFile = if (removeFile) null else existing?.file
                picked?.let { file ->
                    uploadProgress = 0
                    finalFile = withContext(Dispatchers.IO) { container.api.uploadVaultFile(file.name, file.mime, file.size, { file.open(context) }) { loaded, total -> uploadProgress = if (total > 0) ((loaded * 100) / total).toInt() else null } }
                    uploadedKey = finalFile?.key.orEmpty()
                    uploadProgress = null
                }
                val draft = VaultItem(
                    id = existing?.id ?: UUID.randomUUID().toString(), section = "notes", title = title.trim(), subtitle = existing?.subtitle, metadata = meta,
                    createdAt = existing?.createdAt ?: Dates.nowIso(), updatedAt = Dates.nowIso(), file = finalFile, favorite = existing?.favorite ?: false, pinned = existing?.pinned ?: false, folderId = existing?.folderId ?: folderId,
                )
                vault.saveItem(draft)
                existing?.file?.key?.takeIf { it != finalFile?.key }?.let { oldKey -> runCatchingSafe { container.api.deleteVaultFile(oldKey) } }
                if (!quiet) notify(if (isNew) "${kindDef.label} saved." else "Changes saved.", false)
                closeEditor()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                if (uploadedKey.isNotBlank()) runCatchingSafe { container.api.deleteVaultFile(uploadedKey) }
                notify(e.message ?: "Couldn't save.", true)
            } finally { saving = false; uploadProgress = null }
        }
    }

    fun changeAddDocumentDestination() {
        val flow = EditorDrawer.addDocumentDraft ?: return
        val values = mapOf(
            "title" to title, "recordType" to recordType, "content" to content, "tags" to tags,
            "additionalData" to additionalData, "todoDetails" to details, "dueDate" to dueDate,
            "completed" to completed.toString(), "reminderAt" to reminderAt, "alarmTime" to alarmTime,
            "alarmDate" to alarmDate, "repeatDays" to repeatDays, "enabled" to enabled.toString(),
        )
        EditorDrawer.openAddDocumentForChange(flow.copy(file = picked ?: flow.file, values = values))
    }

    if (recordType == "note") {
        // ---- Plain note: phone-notes / Keep style — borderless title, meta line, free-form body, tags at the bottom ----
        val charCount = content.length
        val stamp = remember(existing?.updatedAt) { (existing?.updatedAt?.let { Dates.parseInstant(it) } ?: ZonedDateTime.now()).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM h:mm a")) }
        fun leave() { if (title.isBlank() && content.isBlank()) { closeEditor(); return }; if (dirty) save(quiet = true) else closeEditor() }
        androidx.activity.compose.BackHandler(enabled = true) { leave() }
        val bodyFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        val tint = NoteColors.background(color)
        val fgOnTint = if (tint != null) NoteColors.onTint else Bento.fg
        val mutedOnTint = if (tint != null) NoteColors.onTintMuted else Bento.subtleFg
        Scaffold(containerColor = tint ?: androidx.compose.ui.graphics.Color.Transparent, topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = fgOnTint) }
                Spacer(Modifier.weight(1f))
                if (initialFile != null && picked != null && EditorDrawer.addDocumentDraft != null) TextButton(onClick = ::changeAddDocumentDestination, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Space", color = Bento.primary, fontSize = 12.sp) }
                IconButton(onClick = { paletteOpen = !paletteOpen }) { Icon(Icons.Outlined.Palette, "Note colour", tint = if (paletteOpen) Bento.primary else fgOnTint) }
                IconButton(onClick = { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_SUBJECT, title); putExtra(android.content.Intent.EXTRA_TEXT, listOf(title, content).filter { it.isNotBlank() }.joinToString("\n\n")) }, "Share note")) }) { Icon(Icons.Outlined.IosShare, "Share", tint = fgOnTint) }
                IconButton(onClick = { save() }, enabled = !saving) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Bento.primary) else Icon(Icons.Outlined.Check, "Save", tint = Bento.primary) }
            }
        }, bottomBar = {
            // Colour palette (Keep-style) + tags strip (comma separated → chips), like a notes toolbar.
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                androidx.compose.animation.AnimatedVisibility(visible = paletteOpen) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NoteColors.all.forEach { sw ->
                            val dot = NoteColors.dot(sw)
                            val on = color == sw.id
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(dot ?: Bento.card).border(if (on) 2.dp else 1.dp, if (on) Bento.primary else Bento.borderStrong, CircleShape).clickable { color = sw.id },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Icon(Icons.Outlined.Check, sw.label, tint = if (dot == null) Bento.primary else NoteColors.onTint, modifier = Modifier.size(16.dp))
                                else if (dot == null) Icon(Icons.Outlined.FormatColorReset, "Default", tint = Bento.mutedFg, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                val chips = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tag, null, tint = mutedOnTint, modifier = Modifier.size(16.dp))
                    chips.forEach { t -> InputChip(selected = false, onClick = { tags = chips.filterNot { it == t }.joinToString(", ") }, label = { Text(t) }, trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(14.dp)) }, shape = CircleShape, colors = InputChipDefaults.inputChipColors(containerColor = Bento.muted, labelColor = Bento.primary, trailingIconColor = Bento.primary), border = null) }
                    var newTag by remember { mutableStateOf("") }
                    androidx.compose.foundation.text.BasicTextField(newTag, { v -> if (v.endsWith(",")) { val t = v.dropLast(1).trim(); if (t.isNotBlank()) tags = (chips + t).joinToString(", "); newTag = "" } else newTag = v }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = fgOnTint), modifier = Modifier.widthIn(min = 80.dp).padding(horizontal = 4.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { val t = newTag.trim(); if (t.isNotBlank()) tags = (chips + t).joinToString(", "); newTag = "" }),
                        decorationBox = { inner -> Box { if (newTag.isEmpty()) Text(if (chips.isEmpty()) "Add a tag" else "Add", style = MaterialTheme.typography.bodyMedium, color = mutedOnTint); inner() } })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(title, { title = it; error = null }, textStyle = MaterialTheme.typography.headlineMedium.copy(color = fgOnTint, fontWeight = FontWeight.SemiBold), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { bodyFocus.requestFocus() }),
                    decorationBox = { inner -> Box { if (title.isEmpty()) Text("Title", style = MaterialTheme.typography.headlineMedium, color = Bento.borderStrong); inner() } })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stamp, style = MaterialTheme.typography.bodyMedium, color = mutedOnTint)
                    Text("  |  ", color = Bento.borderStrong); Text("$charCount characters", style = MaterialTheme.typography.bodyMedium, color = mutedOnTint)
                }
                Spacer(Modifier.height(18.dp))
                androidx.compose.foundation.text.BasicTextField(content, { if (it.length <= MAX_DETAILS) content = it }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = fgOnTint, lineHeight = 26.sp), modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp).focusRequester(bodyFocus),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    decorationBox = { inner -> Box { if (content.isEmpty()) Text("Start typing", style = MaterialTheme.typography.bodyLarge, color = Bento.borderStrong); inner() } })
                Spacer(Modifier.height(18.dp))
                TextInput(additionalData, { additionalData = it }, "Additional Data", placeholder = "Other information to keep with this note", minLines = 3)
                Spacer(Modifier.height(16.dp))
                NoteAttachmentSection(
                    currentFile = savedFileForEditor, picked = picked, canUpload = canUpload, progress = uploadProgress,
                    scanning = scanning, scanResult = scanResult, scanError = scanError, canScan = canScanNoteFile, fieldLabels = scanFieldLabels,
                    onChooseFile = { filePicker.launch(arrayOf("application/pdf", "image/*", "*/*")) },
                    onRemove = { if (picked != null) picked = null else removeFile = true; scanResult = null; scanError = null },
                    onScan = { if (!scanning) runSmartScan(scanResult != null) },
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Bento.danger, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(120.dp))
            }
        }
        return
    }

    // ---- Task / reminder / alarm: Google Tasks style — borderless title, "Add details" row, chips for date / time / repeat, star in the top bar ----
    var datePicker by remember { mutableStateOf<String?>(null) } // "due" | "reminder" | "alarm"
    var timePicker by remember { mutableStateOf<String?>(null) } // "reminder" | "alarm"
    val reminderZ = remember(reminderAt) { Dates.parseInstant(reminderAt) }
    fun reminderWith(date: LocalDate? = null, time: LocalTime? = null) {
        val base = reminderZ ?: LocalDate.now().plusDays(1).atTime(9, 0).atZone(ZoneId.systemDefault())
        val d = date ?: base.toLocalDate(); val t = time ?: base.toLocalTime()
        reminderAt = d.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toString(); error = null
    }
    fun friendlyDate(iso: String): String = runCatching { LocalDate.parse(iso) }.getOrNull()?.let { d ->
        when (d) { LocalDate.now() -> "Today"; LocalDate.now().plusDays(1) -> "Tomorrow"; else -> d.format(java.time.format.DateTimeFormatter.ofPattern(if (d.year == LocalDate.now().year) "EEE, d MMM" else "d MMM yyyy")) }
    } ?: iso
    val titleFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { if (isNew) runCatching { titleFocus.requestFocus() } }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = closeEditor) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Bento.fg) }
            Text(if (isNew) "New ${kindDef.label.lowercase()}" else kindDef.label, style = MaterialTheme.typography.titleMedium, color = Bento.fg, modifier = Modifier.weight(1f))
            IconButton(onClick = { save() }, enabled = !saving) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Bento.primary) else Icon(Icons.Outlined.Check, "Save", tint = Bento.primary) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 80.dp)) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.text.BasicTextField(title, { title = it.take(240); error = null }, textStyle = MaterialTheme.typography.headlineSmall.copy(color = Bento.fg, fontWeight = FontWeight.SemiBold), modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                decorationBox = { inner -> Box { if (title.isEmpty()) Text("Title", style = MaterialTheme.typography.headlineSmall, color = Bento.borderStrong); inner() } })
            if (recordType == "todo" && !isNew) {
                Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { completed = !completed }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (completed) Bento.primary else Bento.mutedFg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (completed) "Completed — tap to reopen" else "Open — tap to mark done", style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
                }
            }

            Spacer(Modifier.height(14.dp))
            // Details row
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.AutoMirrored.Outlined.Notes, null, tint = Bento.mutedFg, modifier = Modifier.padding(top = 2.dp).size(20.dp)); Spacer(Modifier.width(14.dp))
                androidx.compose.foundation.text.BasicTextField(details, { if (it.length <= MAX_DETAILS) details = it }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Bento.fg, lineHeight = 24.sp), modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    decorationBox = { inner -> Box { if (details.isEmpty()) Text("Add details", style = MaterialTheme.typography.bodyLarge, color = Bento.subtleFg); inner() } })
            }

            Spacer(Modifier.height(18.dp))
            // Schedule row (chips)
            Row(verticalAlignment = Alignment.Top) {
                Icon(if (recordType == "alarm") Icons.Outlined.Alarm else Icons.Outlined.Event, null, tint = Bento.mutedFg, modifier = Modifier.padding(top = 8.dp).size(20.dp)); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    when (recordType) {
                        "todo" -> {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ValueChip(if (dueDate.isBlank()) "Add date" else friendlyDate(dueDate), Icons.Outlined.Event, dueDate.isNotBlank(), onClick = { datePicker = "due" }, onClear = { dueDate = "" })
                            }
                            if (dueDate.isBlank()) QuickDates(onPick = { dueDate = it }, onClear = { dueDate = "" }, hasValue = false)
                        }
                        "reminder" -> {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ValueChip(reminderZ?.toLocalDate()?.let { friendlyDate(it.toString()) } ?: "Add date", Icons.Outlined.Event, reminderZ != null, onClick = { datePicker = "reminder" })
                                ValueChip(reminderZ?.let { Dates.formatClock(String.format("%02d:%02d", it.hour, it.minute)) } ?: "Add time", Icons.Outlined.Schedule, reminderZ != null, onClick = { timePicker = "reminder" })
                                if (reminderZ != null) ValueChip("Clear", Icons.Outlined.Close, false, onClick = { reminderAt = "" })
                            }
                            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                fun at(dt: LocalDateTime) = dt.atZone(ZoneId.systemDefault()).toInstant().toString()
                                val now = LocalDateTime.now()
                                QuickChip("In 1 hour") { reminderAt = at(now.plusHours(1).withSecond(0).withNano(0)) }
                                QuickChip("Tonight 8 PM") { reminderAt = at(LocalDate.now().atTime(20, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }) }
                                QuickChip("Tomorrow 9 AM") { reminderAt = at(LocalDate.now().plusDays(1).atTime(9, 0)) }
                                QuickChip("Next week") { reminderAt = at(LocalDate.now().plusWeeks(1).atTime(9, 0)) }
                            }
                        }
                        "alarm" -> {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ValueChip(Dates.formatClock(alarmTime), Icons.Outlined.Schedule, true, onClick = { timePicker = "alarm" })
                                if (repeatSet.isEmpty()) ValueChip(if (alarmDate.isBlank()) "Add date" else friendlyDate(alarmDate), Icons.Outlined.Event, alarmDate.isNotBlank(), onClick = { datePicker = "alarm" }, onClear = { alarmDate = "" })
                                else ValueChip(Dates.repeatLabel(repeatDays), Icons.Outlined.Repeat, true, onClick = {}, onClear = { repeatDays = "" })
                            }
                            Text(if (repeatSet.isEmpty()) "One-time alarm — or pick the days it should repeat." else "Repeats weekly on the selected days.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                WEEKDAYS.forEach { (id, label) ->
                                    val on = id in repeatSet
                                    Box(
                                        Modifier.size(38.dp).clip(CircleShape).background(if (on) Bento.primary else Bento.muted).border(1.dp, if (on) Bento.primary else Bento.border, CircleShape)
                                            .clickable { repeatDays = (if (on) repeatSet - id else repeatSet + id).sorted().joinToString(","); error = null },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(label.take(1), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (on) Bento.primaryFg else Bento.fg) }
                                }
                            }
                            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                QuickChip("Every day") { repeatDays = "0,1,2,3,4,5,6" }
                                QuickChip("Weekdays") { repeatDays = "1,2,3,4,5" }
                                QuickChip("Weekends") { repeatDays = "0,6" }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            TextInput(additionalData, { additionalData = it }, "Additional Data", placeholder = "Other information to keep with this ${kindDef.label.lowercase()}", minLines = 3)
            Spacer(Modifier.height(16.dp))
            NoteAttachmentSection(
                currentFile = savedFileForEditor, picked = picked, canUpload = canUpload, progress = uploadProgress,
                scanning = scanning, scanResult = scanResult, scanError = scanError, canScan = canScanNoteFile, fieldLabels = scanFieldLabels,
                onChooseFile = { filePicker.launch(arrayOf("application/pdf", "image/*", "*/*")) },
                onRemove = { if (picked != null) picked = null else removeFile = true; scanResult = null; scanError = null },
                onScan = { if (!scanning) runSmartScan(scanResult != null) },
            )

            if (recordType == "reminder" || recordType == "alarm") {
                Spacer(Modifier.height(18.dp))
                // Ringtone row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MusicNote, null, tint = Bento.mutedFg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp))
                    Box(Modifier.weight(1f)) { SelectInput(ringtoneName, ringtoneOptions.map { it.second }, { picked -> ringtoneOptions.firstOrNull { it.second == picked }?.let { ringtoneId = it.first } }, "Sound", allowEmpty = false) }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = { RingtonePreview.toggle(context, ringtoneId) { previewing = it } }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Bento.muted, contentColor = Bento.primary)) {
                        Icon(if (previewing == ringtoneId) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, if (previewing == ringtoneId) "Stop preview" else "Preview ringtone")
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = Bento.mutedFg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (enabled) "Active" else "Paused", style = MaterialTheme.typography.bodyLarge, color = Bento.fg)
                        Text(nextRing?.let { "Next: ${Dates.formatDateTime(it.toInstant().toString())}" } ?: if (enabled) "Will ring on this phone and the website." else "Kept in your list but won't ring.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = Bento.primary))
                }
            }

            error?.let { Spacer(Modifier.height(12.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = Bento.danger) }
        }
    }

    datePicker?.let { which ->
        val current = when (which) { "due" -> dueDate; "alarm" -> alarmDate; else -> reminderZ?.toLocalDate()?.toString().orEmpty() }
        PickDateDialog(current, onPick = { picked ->
            when (which) { "due" -> dueDate = picked; "alarm" -> { alarmDate = picked; error = null }; else -> if (picked.isNotBlank()) reminderWith(date = LocalDate.parse(picked)) else reminderAt = "" }
        }, onDismiss = { datePicker = null })
    }
    timePicker?.let { which ->
        val current = if (which == "alarm") alarmTime else reminderZ?.let { String.format("%02d:%02d", it.hour, it.minute) }.orEmpty()
        PickTimeDialog(current, onPick = { picked -> if (which == "alarm") { alarmTime = picked; error = null } else reminderWith(time = LocalTime.parse(picked)) }, onDismiss = { timePicker = null })
    }
}

@Composable
private fun NoteAttachmentSection(
    currentFile: VaultFile?,
    picked: PickedFile?,
    canUpload: Boolean,
    progress: Int?,
    scanning: Boolean,
    scanResult: SmartScanResult?,
    scanError: String?,
    canScan: Boolean,
    fieldLabels: Map<String, String>,
    onChooseFile: () -> Unit,
    onRemove: () -> Unit,
    onScan: () -> Unit,
) {
    BentoCard {
        SectionHeading("Attachment", if (picked != null || currentFile != null) "Current file" else "Optional · private")
        if (picked != null || currentFile != null) {
            val name = picked?.name ?: currentFile?.name.orEmpty()
            val size = picked?.size ?: currentFile?.size ?: 0L
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                ToneIconBox(Icons.Outlined.AttachFile, Tones.Blue, size = 34.dp, radius = 10.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Files.formatSize(size), style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Remove attachment", tint = Bento.danger) }
            }
        } else Text("Attach a PDF or image to scan, or keep a file with this entry. Uploads support files up to 25 MB; Smart Scan supports 7 MB.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
        if (!canUpload) Text("New attachments and Smart Scan require an active paid plan. Existing files remain accessible.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 5.dp))
        Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Choose file", onClick = onChooseFile, icon = Icons.Outlined.UploadFile, enabled = canUpload)
            SoftButton(if (scanning) "Scanning…" else if (scanResult != null) "Scan again" else "Smart Scan", onClick = onScan, icon = Icons.Outlined.AutoAwesome, enabled = !scanning && canUpload && canScan)
        }
        progress?.let { Text("Uploading $it%", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.padding(top = 5.dp)) }
        scanError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Bento.danger, modifier = Modifier.padding(top = 6.dp)) }
        scanResult?.let { result ->
            Spacer(Modifier.height(8.dp)); HorizontalDivider(color = Bento.border); Spacer(Modifier.height(6.dp))
            if (result.documentType.isNotBlank()) Pill("Looks like: ${result.documentType} · ${result.documentTypeConfidence}", Tones.Violet)
            Text("${result.fields.values.count { it.value.isNotBlank() }} suggestions applied · review before saving", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.padding(top = 4.dp))
            result.fields.filterValues { it.value.isNotBlank() }.forEach { (key, value) ->
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(fieldLabels[key] ?: key, style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg); Text(value.value, style = MaterialTheme.typography.bodySmall, color = Bento.fg, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Pill(value.confidence, if (value.confidence == "high") Tones.Green else if (value.confidence == "medium") Tones.Amber else Tones.Neutral)
                }
            }
            result.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Accents.amber.text, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, colors = AssistChipDefaults.assistChipColors(containerColor = Bento.card, labelColor = Bento.fg), border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = Bento.border))
}

@Composable
private fun QuickDates(onPick: (String) -> Unit, onClear: () -> Unit, hasValue: Boolean) {
    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        QuickChip("Today") { onPick(LocalDate.now().toString()) }
        QuickChip("Tomorrow") { onPick(LocalDate.now().plusDays(1).toString()) }
        QuickChip("Next week") { onPick(LocalDate.now().plusWeeks(1).toString()) }
        if (hasValue) QuickChip("Clear", onClear)
    }
}

