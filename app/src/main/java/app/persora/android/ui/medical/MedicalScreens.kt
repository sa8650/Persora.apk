package app.persora.android.ui.medical

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import app.persora.android.core.util.PickedFile
import app.persora.android.data.model.MEDICAL_RECORD_TYPES
import app.persora.android.data.model.MedicalRecord
import app.persora.android.data.model.MedicalRecordLink
import app.persora.android.data.model.SmartScanFieldDefinition
import app.persora.android.data.model.SmartScanResult
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.EditorDrawer
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private fun typeTone(t: String) = when (t) { "Prescription" -> Tones.Violet; "Lab Test" -> Tones.Teal; "Imaging / Scan" -> Tones.Indigo; "Vaccination" -> Tones.Green; "Hospital Record", "Discharge Summary" -> Tones.Red; "Doctor Visit" -> Tones.Blue; else -> Tones.Slate }

private const val SMART_SCAN_MAX_BYTES = 7L * 1024 * 1024
private fun supportsMedicalSmartScan(file: PickedFile): Boolean {
    val mime = file.mime.lowercase().substringBefore(';')
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return file.size in 1L..SMART_SCAN_MAX_BYTES && (mime in setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "image/gif", "image/tiff", "image/bmp") || ext in setOf("pdf", "jpg", "jpeg", "png", "webp", "gif", "tif", "tiff", "bmp"))
}

private fun medicalScanFields(recordType: String): List<SmartScanFieldDefinition> = buildList {
    add(SmartScanFieldDefinition("title", "Record Title", "text"))
    add(SmartScanFieldDefinition("recordType", "Record Type", "select", MEDICAL_RECORD_TYPES))
    add(SmartScanFieldDefinition("recordDate", "Record Date", "date"))
    add(SmartScanFieldDefinition("provider", "Doctor / Healthcare Provider", "text"))
    add(SmartScanFieldDefinition("hospital", "Hospital / Clinic", "text"))
    add(SmartScanFieldDefinition("specialty", "Medical Specialty", "text"))
    if (recordType !in setOf("Vaccination", "Medical Certificate", "Other")) add(SmartScanFieldDefinition("diagnosis", "Diagnosis / Reason for Visit", "text"))
    if (recordType in setOf("Lab Test", "Imaging / Scan", "Medical Report")) {
        add(SmartScanFieldDefinition("testName", "Test Name", "text"))
        add(SmartScanFieldDefinition("testResult", "Test Result Summary", "text"))
    }
    if (recordType == "Prescription" || recordType == "Doctor Visit") add(SmartScanFieldDefinition("medicationNotes", "Prescription / Medication Notes", "textarea"))
    if (recordType in setOf("Doctor Visit", "Lab Test", "Imaging / Scan", "Hospital Record", "Discharge Summary")) add(SmartScanFieldDefinition("followUpDate", "Follow-up Date", "date"))
    add(SmartScanFieldDefinition("notes", "Notes", "textarea"))
    add(SmartScanFieldDefinition("additionalData", "Additional Data", "textarea"))
}

@Composable
fun MedicalRecordsScreen() {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val records by vault.medicalRecords.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("All") }
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<MedicalRecord?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vault.refreshMedical() }

    val visible = remember(records, query, type) { records.filter { (type == "All" || it.recordType == type) && (query.isBlank() || listOf(it.title, it.provider, it.hospital, it.diagnosis, it.testName).any { v -> v.contains(query, true) }) }.sortedByDescending { it.recordDate.ifBlank { it.createdAt } } }
    val followUps = remember(records) { records.mapNotNull { r -> Dates.daysUntil(r.followUpDate)?.takeIf { it >= 0 && it <= 30 }?.let { r to it } }.sortedBy { it.second } }

    fun open(r: MedicalRecord) { opening = r.id; scope.launch { try { val uri = withContext(Dispatchers.IO) { container.api.openMedicalFile(r.id).use { Files.stash(context, it.name, it.stream) } }; context.startActivity(Files.viewIntent(uri, r.file?.type ?: "*/*")) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Could not open the file.", true) } finally { opening = null } } }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, floatingActionButton = { ExtendedFloatingActionButton(onClick = { EditorDrawer.openMedical() }, containerColor = Bento.primary, contentColor = Bento.primaryFg, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SearchField(query, { query = it }, "Search diagnoses, doctors, tests…")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (listOf("All") + MEDICAL_RECORD_TYPES).forEach { t -> FilterChip(type == t, { type = t }, { Text(t) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bento.muted, selectedLabelColor = Bento.primary)) } }
            }
            if (followUps.isNotEmpty()) item {
                BentoCard(padding = 12.dp) {
                    SectionHeading("Follow-ups", "Next 30 days", followUps.size)
                    followUps.take(3).forEach { (r, d) -> Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.EventRepeat, null, tint = Accents.amber.text, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(r.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Pill(if (d == 0L) "Today" else "in ${d}d", Tones.Amber) } }
                }
            }
            if (visible.isEmpty()) item { EmptyState(if (records.isEmpty()) "No medical records yet." else "Nothing matches.", if (records.isEmpty()) "Add a prescription or lab report—attach the PDF or photo and Persora keeps it with the date, doctor and follow-up." else "Try another search or type.", Icons.Outlined.MonitorHeart) }
            items(visible, key = { it.id }) { r ->
                val tone = typeTone(r.recordType)
                BentoCard(onClick = { expanded = if (expanded == r.id) null else r.id }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIconBox(Icons.Outlined.MedicalServices, tone, size = 40.dp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(r.recordType, Dates.formatDate(r.recordDate).takeIf { it.isNotBlank() }, r.provider.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (r.file != null) Icon(Icons.Outlined.AttachFile, null, tint = Bento.subtleFg, modifier = Modifier.size(16.dp))
                        Icon(if (expanded == r.id) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Bento.subtleFg)
                    }
                    if (expanded == r.id) {
                        Spacer(Modifier.height(10.dp)); HorizontalDivider(color = Bento.border); Spacer(Modifier.height(6.dp))
                        if (r.hospital.isNotBlank()) DetailRow("Hospital / clinic", r.hospital)
                        if (r.specialty.isNotBlank()) DetailRow("Specialty", r.specialty)
                        if (r.diagnosis.isNotBlank()) DetailRow("Diagnosis", r.diagnosis)
                        if (r.testName.isNotBlank()) DetailRow("Test", r.testName + (r.testResult.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""))
                        if (r.medicationNotes.isNotBlank()) DetailRow("Medication", r.medicationNotes)
                        if (r.followUpDate.isNotBlank()) DetailRow("Follow-up", Dates.formatDate(r.followUpDate))
                        if (r.notes.isNotBlank()) DetailRow("Notes", r.notes)
                        if (r.additionalData.isNotBlank()) DetailRow("Additional Data", r.additionalData)
                        if (r.links.isNotEmpty()) DetailRow("Linked", "${r.links.size} related record${if (r.links.size == 1) "" else "s"}")
                        r.file?.let { f -> if (Files.isPdf(f.type, f.name) || Files.isText(f.type, f.name)) { Spacer(Modifier.height(10.dp)); InlineFilePreview("medical:${r.id}", f.name, f.type, download = { container.api.openMedicalFile(r.id).stream }) } }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.file != null) SoftButton(if (opening == r.id) "Opening…" else "Open file", onClick = { if (opening == null) open(r) }, icon = Icons.Outlined.OpenInNew)
                            QuietButton("Edit", onClick = { EditorDrawer.openMedical(r.id) }, icon = Icons.Outlined.Edit)
                            TextButton(onClick = { confirmDelete = r }) { Text("Delete", color = Bento.danger) }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { r -> ConfirmDialog("Delete “${r.title}”?", "The record and its file are removed from your vault.", onConfirm = { confirmDelete = null; scope.launch { runCatchingSafe { vault.deleteMedicalRecord(r.id) }.onSuccess { notify("Record deleted.", false) }.onFailure { notify(it.message ?: "Could not delete.", true) } } }, onDismiss = { confirmDelete = null }) }
}

@Composable
fun MedicalEditorScreen(id: String?, initialType: String? = null, initialFile: PickedFile? = null, initialTitle: String = "", initialAdditionalData: String = "", initialScanResult: SmartScanResult? = null, initialScanComplete: Boolean = false, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val nav = LocalNav.current
    val closeEditor: () -> Unit = onClose ?: { nav.popBackStack() }
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val records by vault.medicalRecords.collectAsStateWithLifecycle()
    val items by vault.items.collectAsStateWithLifecycle()
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    val existing = records.firstOrNull { it.id == id }
    val canUpload = observeCurrentUser()?.uploadsEnabled == true
    var draft by remember(existing, initialType, initialTitle, initialAdditionalData) { mutableStateOf(existing ?: MedicalRecord(id = UUID.randomUUID().toString(), title = initialTitle, recordType = initialType ?: "Other", recordDate = Dates.today(), additionalData = initialAdditionalData)) }
    var picked by remember(initialFile) { mutableStateOf(initialFile) }
    var autoScannedUri by remember { mutableStateOf<Uri?>(null) }
    var lastAutoScanSchema by remember(initialFile) { mutableStateOf<String?>(null) }
    var autoScanAppliedOnce by remember(initialFile) { mutableStateOf(false) }
    var autoAppliedScanValues by remember(initialFile) { mutableStateOf(emptyMap<String, String>()) }
    var userEditedScanFields by remember(initialFile) { mutableStateOf(emptySet<String>()) }
    var removeFile by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanResult by remember(initialScanResult) { mutableStateOf(initialScanResult) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var createReminder by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && canUpload) { val f = Files.describe(context, uri); if (f.size > 25L * 1024 * 1024) notify("Files must be 25 MB or smaller.", true) else { picked = f; removeFile = false; scanResult = null; scanError = null } }
        else if (uri != null) notify("New medical-file uploads require an active paid plan. Existing files remain accessible.", true)
    }
    val doctors = remember(contacts) { contacts.filter { it.category == "Service" || it.jobTitle.contains("doctor", true) || it.jobTitle.startsWith("Dr", true) || it.name.startsWith("Dr", true) } }
    val relatedDocs = remember(items) { items.filter { it.section == "documents" || it.section == "family" } }
    val scanSchemaSignature = "${draft.recordType}::${medicalScanFields(draft.recordType).joinToString(",") { it.key }}"

    fun medicalFieldValue(record: MedicalRecord, key: String): String = when (key) {
        "title" -> record.title
        "recordType" -> record.recordType
        "recordDate" -> record.recordDate
        "provider" -> record.provider
        "hospital" -> record.hospital
        "specialty" -> record.specialty
        "diagnosis" -> record.diagnosis
        "testName" -> record.testName
        "testResult" -> record.testResult
        "medicationNotes" -> record.medicationNotes
        "followUpDate" -> record.followUpDate
        "notes" -> record.notes
        "additionalData" -> record.additionalData
        else -> ""
    }
    fun setMedicalFieldFromUser(key: String, value: String) {
        userEditedScanFields = userEditedScanFields + key
        draft = when (key) {
            "title" -> draft.copy(title = value)
            "recordType" -> draft.copy(recordType = value.takeIf { it in MEDICAL_RECORD_TYPES } ?: draft.recordType)
            "recordDate" -> draft.copy(recordDate = value)
            "provider" -> draft.copy(provider = value)
            "hospital" -> draft.copy(hospital = value)
            "specialty" -> draft.copy(specialty = value)
            "diagnosis" -> draft.copy(diagnosis = value)
            "testName" -> draft.copy(testName = value)
            "testResult" -> draft.copy(testResult = value)
            "medicationNotes" -> draft.copy(medicationNotes = value)
            "followUpDate" -> draft.copy(followUpDate = value)
            "notes" -> draft.copy(notes = value)
            "additionalData" -> draft.copy(additionalData = value)
            else -> draft
        }
    }

    fun runSmartScan(retry: Boolean = false) {
        if (!canUpload) { scanError = "Smart Scan uploads require an active paid plan."; return }
        val file = picked ?: run { scanError = "Choose a file to scan."; return }
        if (!supportsMedicalSmartScan(file)) { scanError = "Smart Scan supports PDF and common image files up to 7 MB."; return }
        val requestedSchema = scanSchemaSignature
        val definitions = medicalScanFields(draft.recordType)
        if (initialFile?.uri == file.uri) {
            autoScannedUri = file.uri
            lastAutoScanSchema = requestedSchema
        }
        scanning = true; scanError = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { container.api.smartScan(file.name, file.mime, file.size, { file.open(context) }, "medical-records", definitions, retry) }
                scanResult = result
                val previousAutoValues = autoAppliedScanValues
                val appliedValues = previousAutoValues.toMutableMap()
                val firstAppliedScan = !autoScanAppliedOnce
                var updated = draft
                result.fields.forEach { (key, value) ->
                    if (value.value.isBlank() || definitions.none { it.key == key } || key in userEditedScanFields) return@forEach
                    val currentValue = medicalFieldValue(updated, key)
                    val canApply = firstAppliedScan || currentValue.isBlank() || previousAutoValues[key] == currentValue ||
                        (key == "additionalData" && currentValue == initialAdditionalData) || (key == "title" && currentValue == initialTitle)
                    if (!canApply) return@forEach
                    updated = when (key) {
                        "title" -> updated.copy(title = value.value)
                        "recordType" -> updated.copy(recordType = value.value.takeIf { it in MEDICAL_RECORD_TYPES } ?: updated.recordType)
                        "recordDate" -> updated.copy(recordDate = value.value)
                        "provider" -> updated.copy(provider = value.value)
                        "hospital" -> updated.copy(hospital = value.value)
                        "specialty" -> updated.copy(specialty = value.value)
                        "diagnosis" -> updated.copy(diagnosis = value.value)
                        "testName" -> updated.copy(testName = value.value)
                        "testResult" -> updated.copy(testResult = value.value)
                        "medicationNotes" -> updated.copy(medicationNotes = value.value)
                        "followUpDate" -> updated.copy(followUpDate = value.value)
                        "notes" -> updated.copy(notes = value.value)
                        "additionalData" -> updated.copy(additionalData = value.value.take(5000))
                        else -> updated
                    }
                    appliedValues[key] = medicalFieldValue(updated, key)
                }
                draft = updated
                autoAppliedScanValues = appliedValues
                autoScanAppliedOnce = true
                if (initialFile?.uri == file.uri) lastAutoScanSchema = requestedSchema
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { scanError = e.message ?: "Smart Scan failed. Please retry." }
            finally { scanning = false }
        }
    }

    LaunchedEffect(initialFile?.uri, picked?.uri, canUpload, initialScanComplete, scanSchemaSignature, scanning, autoScanAppliedOnce) {
        val initial = initialFile ?: return@LaunchedEffect
        if (picked?.uri != initial.uri || !canUpload || !supportsMedicalSmartScan(initial) || scanning) return@LaunchedEffect
        if (initialScanComplete && !autoScanAppliedOnce) return@LaunchedEffect
        if (autoScannedUri != initial.uri || lastAutoScanSchema != scanSchemaSignature) {
            autoScannedUri = initial.uri
            lastAutoScanSchema = scanSchemaSignature
            runSmartScan(false)
        }
    }

    fun changeAddDocumentDestination() {
        val flow = EditorDrawer.addDocumentDraft ?: return
        val values = mapOf(
            "title" to draft.title, "recordType" to draft.recordType, "recordDate" to draft.recordDate,
            "provider" to draft.provider, "hospital" to draft.hospital, "specialty" to draft.specialty,
            "diagnosis" to draft.diagnosis, "testName" to draft.testName, "testResult" to draft.testResult,
            "medicationNotes" to draft.medicationNotes, "followUpDate" to draft.followUpDate,
            "notes" to draft.notes, "additionalData" to draft.additionalData,
        )
        EditorDrawer.openAddDocumentForChange(flow.copy(file = picked ?: flow.file, values = values))
    }

    fun save() {
        if (picked != null && !canUpload) { notify("New medical-file uploads require an active paid plan. Existing files remain accessible.", true); return }
        if (draft.title.isBlank()) { notify("Give the record a title.", true); return }
        saving = true
        scope.launch {
            try {
                var file = if (removeFile) null else existing?.file
                picked?.let { p -> progress = 0; file = withContext(Dispatchers.IO) { container.api.uploadMedicalFile(p.name, p.mime, p.size, { p.open(context) }) { l, t -> progress = if (t > 0) ((l * 100) / t).toInt() else null } }; progress = null }
                var reminderId = draft.relatedReminderId
                if (createReminder && draft.followUpDate.isNotBlank()) {
                    val at = Dates.parseLocalDate(draft.followUpDate)!!.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toString()
                    val reminder = vault.saveItem(app.persora.android.data.model.VaultItem(id = UUID.randomUUID().toString(), section = "notes", title = "Follow-up: ${draft.title}", metadata = mapOf("recordType" to "reminder", "reminderAt" to at, "enabled" to "true", "ringtoneId" to "builtin-soft", "ringtoneName" to "Persora soft chime", "todoDetails" to listOf(draft.provider, draft.hospital).filter { it.isNotBlank() }.joinToString(" · ")), createdAt = Dates.nowIso(), updatedAt = Dates.nowIso()))
                    reminderId = reminder.id
                }
                vault.saveMedicalRecord(draft.copy(file = file, relatedReminderId = reminderId), existing == null, removeFile && picked == null)
                existing?.file?.key?.takeIf { (removeFile || picked != null) && it != file?.key }?.let { k -> runCatchingSafe { container.api.deleteMedicalFileByKey(k) } }
                notify(if (existing == null) "Medical record saved." else "Changes saved.", false); closeEditor()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Could not save.", true) } finally { saving = false; progress = null }
        }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = closeEditor) { Icon(Icons.Outlined.Close, "Cancel", tint = Bento.mutedFg) }
            Column(Modifier.weight(1f)) { Eyebrow("Health archive"); Text(if (existing == null) "New medical record" else "Edit record", style = MaterialTheme.typography.titleMedium, color = Bento.fg) }
            if (initialFile != null && picked != null && EditorDrawer.addDocumentDraft != null) TextButton(onClick = ::changeAddDocumentDestination) { Text("Change space", color = Bento.primary) }
            PrimaryButton(progress?.let { "Uploading $it%" } ?: "Save", ::save, enabled = !saving, loading = saving)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoCard {
                TextInput(draft.title, { setMedicalFieldFromUser("title", it) }, "Title", required = true, placeholder = "e.g. Blood test – March")
                Spacer(Modifier.height(10.dp)); SelectInput(draft.recordType, MEDICAL_RECORD_TYPES, { setMedicalFieldFromUser("recordType", it) }, "Record type", allowEmpty = false)
                Spacer(Modifier.height(10.dp)); DateInput(draft.recordDate, { setMedicalFieldFromUser("recordDate", it) }, "Record date", required = true)
                Spacer(Modifier.height(10.dp))
                if (doctors.isNotEmpty()) SelectInput(draft.provider, doctors.map { it.name }, { setMedicalFieldFromUser("provider", it) }, "Doctor / provider (from contacts)") else TextInput(draft.provider, { setMedicalFieldFromUser("provider", it) }, "Doctor / provider")
                if (doctors.isNotEmpty()) { Spacer(Modifier.height(6.dp)); TextInput(draft.provider, { setMedicalFieldFromUser("provider", it) }, "Or type a name") }
                Spacer(Modifier.height(10.dp)); TextInput(draft.hospital, { setMedicalFieldFromUser("hospital", it) }, "Hospital / clinic")
                Spacer(Modifier.height(10.dp)); TextInput(draft.specialty, { setMedicalFieldFromUser("specialty", it) }, "Specialty", placeholder = "Cardiology, Dermatology…")
            }
            BentoCard {
                SectionHeading("Clinical details", "Optional")
                TextInput(draft.diagnosis, { setMedicalFieldFromUser("diagnosis", it) }, "Diagnosis", minLines = 2)
                if (draft.recordType == "Lab Test" || draft.recordType == "Imaging / Scan") { Spacer(Modifier.height(10.dp)); TextInput(draft.testName, { setMedicalFieldFromUser("testName", it) }, "Test name"); Spacer(Modifier.height(10.dp)); TextInput(draft.testResult, { setMedicalFieldFromUser("testResult", it) }, "Result / summary", minLines = 2) }
                if (draft.recordType == "Prescription" || draft.recordType == "Doctor Visit") { Spacer(Modifier.height(10.dp)); TextInput(draft.medicationNotes, { setMedicalFieldFromUser("medicationNotes", it) }, "Medication & dosage", minLines = 3) }
                Spacer(Modifier.height(10.dp)); TextInput(draft.notes, { setMedicalFieldFromUser("notes", it) }, "Notes", minLines = 3)
                Spacer(Modifier.height(10.dp)); TextInput(draft.additionalData, { setMedicalFieldFromUser("additionalData", it) }, "Additional Data", placeholder = "Other extracted information that does not fit above", minLines = 3)
                Spacer(Modifier.height(10.dp)); DateInput(draft.followUpDate, { setMedicalFieldFromUser("followUpDate", it) }, "Follow-up date")
                if (draft.followUpDate.isNotBlank() && draft.relatedReminderId == null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(createReminder, { createReminder = it }, colors = CheckboxDefaults.colors(checkedColor = Bento.primary)); Text("Also create a reminder at 9:00 that day", style = MaterialTheme.typography.bodyMedium) }
            }
            BentoCard {
                SectionHeading("Attachment", if (existing?.file != null && !removeFile && picked == null) "Current file" else "Report, scan or photo")
                val current = picked?.let { it.name to Files.formatSize(it.size) } ?: existing?.file?.takeIf { !removeFile }?.let { it.name to Files.formatSize(it.size) }
                if (current != null) Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { ToneIconBox(Icons.Outlined.AttachFile, Tones.Red, size = 34.dp, radius = 10.dp); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(current.first, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(current.second, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }; IconButton(onClick = { if (picked != null) picked = null else removeFile = true }) { Icon(Icons.Outlined.Delete, "Remove", tint = Bento.danger) } }
                if (!canUpload) Text("New reports, scans and photos need an active paid plan. You can still edit the record; its existing file remains accessible.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(6.dp)); SoftButton("Choose file", onClick = { if (canUpload) picker.launch(arrayOf("application/pdf", "image/*")) }, icon = Icons.Outlined.UploadFile, enabled = canUpload)
                Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Bento.border); Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIconBox(Icons.Outlined.DocumentScanner, Tones.Violet, size = 34.dp); Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Smart Scan", style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                        Text(when { picked == null -> "Attach a PDF or image to fill matching fields."; !supportsMedicalSmartScan(picked!!) -> "Smart Scan supports files up to 7 MB in PDF or common image formats."; else -> "Review extracted details before saving." }, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    SoftButton(if (scanning) "Scanning…" else if (scanResult != null) "Scan again" else "Scan", onClick = { if (!scanning) runSmartScan(scanResult != null) }, icon = Icons.Outlined.AutoAwesome, enabled = !scanning && canUpload && picked?.let(::supportsMedicalSmartScan) == true)
                }
                scanError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Bento.danger, modifier = Modifier.padding(start = 43.dp, top = 6.dp)) }
                scanResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    if (result.documentType.isNotBlank()) Pill("Looks like: ${result.documentType} · ${result.documentTypeConfidence}", Tones.Violet)
                    Text("${result.fields.values.count { it.value.isNotBlank() }} field suggestions were applied. Review them before saving.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.padding(top = 5.dp))
                    result.fields.filterValues { it.value.isNotBlank() }.forEach { (key, suggestion) ->
                        val label = medicalScanFields(draft.recordType).firstOrNull { it.key == key }?.label ?: key
                        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg); Text(suggestion.value, style = MaterialTheme.typography.bodySmall, color = Bento.fg, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            Pill(suggestion.confidence, if (suggestion.confidence == "high") Tones.Green else if (suggestion.confidence == "medium") Tones.Amber else Tones.Neutral)
                        }
                    }
                    result.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Accents.amber.text, modifier = Modifier.padding(top = 5.dp)) }
                }
            }
            if (relatedDocs.isNotEmpty()) BentoCard {
                SectionHeading("Link related records", "${draft.links.size} linked")
                relatedDocs.take(30).forEach { d ->
                    val linked = draft.links.any { it.recordId == d.id }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(linked, { on -> draft = draft.copy(links = if (on) draft.links + MedicalRecordLink("vault_item", d.id) else draft.links.filterNot { it.recordId == d.id }) }, colors = CheckboxDefaults.colors(checkedColor = Bento.primary))
                        Column { Text(d.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(app.persora.android.data.model.Sections[d.section].label, style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg) }
                    }
                }
            }
        }
    }
}
