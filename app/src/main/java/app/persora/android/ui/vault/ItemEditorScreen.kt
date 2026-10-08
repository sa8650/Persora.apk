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
import app.persora.android.ui.navigation.EditorDrawer
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
private const val MAX_SMART_SCAN_BYTES = 7L * 1024 * 1024
private fun supportsSmartScanFile(file: PickedFile): Boolean {
    val mime = file.mime.lowercase().substringBefore(';')
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return file.size in 1L..MAX_SMART_SCAN_BYTES && (mime in setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "image/gif", "image/tiff", "image/bmp") || ext in setOf("pdf", "jpg", "jpeg", "png", "webp", "gif", "tif", "tiff", "bmp"))
}

/**
 * ItemEditorDialog from VaultDialogs.tsx: builds the form from SECTION_DEFINITIONS, handles the four notes record
 * types, masks card numbers for wallet-cards, uploads attachments to R2 through /api/upload and runs Smart Scan.
 */
@Composable
fun ItemEditorScreen(sectionId: String, itemId: String?, folderId: String?, kind: String?, shareId: String?, initialMetadata: Map<String, String> = emptyMap(), initialFile: PickedFile? = null, initialScanResult: SmartScanResult? = null, initialScanComplete: Boolean = false, onClose: (() -> Unit)? = null) {
    if (sectionId == "notes" && shareId == null) { NotesEditorScreen(itemId, folderId, kind, initialMetadata, initialFile, initialScanResult, initialScanComplete, onClose); return }
    val section = Sections[sectionId]
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val api = container.api
    val nav = LocalNav.current
    val closeEditor: () -> Unit = onClose ?: { nav.popBackStack() }
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val items by vault.items.collectAsStateWithLifecycle()
    val incoming by vault.incomingShares.collectAsStateWithLifecycle()
    val ringtones by vault.ringtones.collectAsStateWithLifecycle()
    val existing = remember(items, incoming) { items.firstOrNull { it.id == itemId } ?: incoming.firstOrNull { it.item.id == itemId }?.item }
    val familyMembers = remember(items) { items.filter { it.section == "family" } }
    val isNew = existing == null
    var remoteDocumentTypes by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(sectionId) {
        if (sectionId == "documents") {
            remoteDocumentTypes = withContext(Dispatchers.IO) {
                runCatching { api.loadDocumentTypes().filter { it.active }.sortedBy { it.sort_order }.map { it.name } }.getOrDefault(emptyList())
            }
        }
    }
    val canUpload = observeCurrentUser()?.uploadsEnabled == true

    var title by rememberSaveable(sectionId, itemId, kind, initialMetadata["title"]) { mutableStateOf(existing?.title ?: initialMetadata["title"].orEmpty()) }
    var metadata by remember(existing?.id, sectionId, kind, initialMetadata) { mutableStateOf(existing?.metadata ?: (buildMap { if (sectionId == "notes") put("recordType", kind ?: "note"); if (sectionId == "notes" && (kind == "reminder" || kind == "alarm")) { put("enabled", "true"); put("ringtoneId", BUILTIN_RINGTONES.first().id); put("ringtoneName", BUILTIN_RINGTONES.first().name) } } + initialMetadata)) }
    var cardNumber by rememberSaveable(itemId) { mutableStateOf("") }
    var expiry by rememberSaveable(itemId) { mutableStateOf(walletExpiry(existing?.metadata)) }
    LaunchedEffect(itemId, existing?.id, existing?.metadata?.get("expiryMonth"), existing?.metadata?.get("expiryYear"), existing?.metadata?.get("expiry")) {
        if (itemId != null && existing != null) expiry = walletExpiry(existing.metadata)
    }
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
    var scan by remember(initialScanResult) { mutableStateOf(initialScanResult) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val recordType = metadata["recordType"] ?: ""
    val isWallet = sectionId == "wallet-cards"
    val familyNames = listOf("Me") + familyMembers.map { it.title }.filter(String::isNotBlank)
    val baseFormFields = Sections.editorFields(sectionId, metadata["type"].orEmpty(), metadata["accountKind"].orEmpty(), metadata["financeType"].orEmpty(), metadata["materialType"].orEmpty())
        .map { if (it.key == "member") it.copy(options = familyNames.distinct()) else it }
    val formFields = if (sectionId == "documents") {
        val fallback = Sections[sectionId].fields.firstOrNull { it.key == "type" }?.options.orEmpty()
        val typeOptions = (remoteDocumentTypes.ifEmpty { fallback } + "CV / Resume" + listOfNotNull(metadata["type"]?.takeIf { it.isNotBlank() })).distinct()
        baseFormFields.map { if (it.key == "type") it.copy(options = typeOptions) else it }
    } else baseFormFields
    val scanFieldDefinitions = formFields.filter { it.key != "lastFour" && it.key != "expiryMonth" && it.key != "expiryYear" && it.key != "cardholder" }
    val scanSchemaSignature = listOf(sectionId, metadata["type"].orEmpty(), metadata["accountKind"].orEmpty(), metadata["financeType"].orEmpty(), metadata["materialType"].orEmpty(), scanFieldDefinitions.joinToString(",") { it.key }).joinToString("::")
    val autoFillFileFlow = initialFile != null

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatchingSafe { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (!canUpload) { notify("New file uploads require an active paid plan. Existing files remain accessible.", true); return@rememberLauncherForActivityResult }
        val f = Files.describe(context, uri)
        if (f.size > MAX_UPLOAD_BYTES) notify("Files must be 25 MB or smaller.", true) else { picked = f; removeFile = false }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && canUpload) cameraUri?.let { picked = Files.describe(context, it).copy(name = "scan-${System.currentTimeMillis()}.jpg", mime = "image/jpeg"); removeFile = false }
        else if (ok) notify("New photo uploads require an active paid plan. Existing files remain accessible.", true)
    }

    fun set(key: String, value: String) { metadata = metadata + (key to value); if (errors.containsKey(key)) errors = errors - key }
    fun setFromUser(key: String, value: String) {
        userEditedScanFields = userEditedScanFields + key
        set(key, value)
    }

    LaunchedEffect(sectionId, metadata["member"], familyMembers) {
        if (sectionId == "documents") {
            val saved = metadata["member"].orEmpty()
            val familyMatch = familyMembers.firstOrNull { it.id == saved || it.title.equals(saved, ignoreCase = true) }
            val normalized = when { saved.equals("Me", ignoreCase = true) -> "me"; familyMatch != null -> familyMatch.id; else -> saved }
            if (saved.isNotBlank() && normalized != saved) set("member", normalized)
        }
    }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (title.isBlank()) e["title"] = "Required"
        if (title.length > 240) e["title"] = "Keep it under 240 characters"
        formFields.filter { it.key != "title" && it.required && !(sectionId == "notes" && it.key !in visibleNoteKeys(recordType)) }.forEach { f ->
            // Wallet expiry is edited in a dedicated MM/YY field and stored as expiryMonth/expiryYear, not metadata["expiry"].
            if (isWallet && f.key == "expiry") return@forEach
            if (metadata[f.key].isNullOrBlank()) e[f.key] = "Required"
        }
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
        if (picked != null && !canUpload) { notify("New file uploads require an active paid plan. Existing files remain accessible.", true); return }
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
                if (sectionId == "notes") { val keep = visibleNoteKeys(recordType) + setOf("recordType", "enabled", "ringtoneId", "ringtoneName", "snoozedUntil", "completed", "additionalData"); meta = meta.filterKeys { it in keep }.toMutableMap(); if (recordType == "todo" && meta["completed"] == null) meta["completed"] = "false" }
                else if (!isWallet) { val keep = formFields.map { it.key }.toSet() + "addFlowType" + (if (initialFile != null) setOf("additionalData") else emptySet()); meta = meta.filterKeys { it in keep }.toMutableMap() }
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
                val draft = VaultItem(id = existing?.id ?: UUID.randomUUID().toString(), section = sectionId, title = title.trim(), subtitle = existing?.subtitle, metadata = meta, createdAt = existing?.createdAt ?: Dates.nowIso(), updatedAt = Dates.nowIso(), file = file, favorite = existing?.favorite ?: false, pinned = existing?.pinned ?: false, folderId = existing?.folderId ?: folderId)
                if (shareId != null) { withContext(Dispatchers.IO) { api.saveSharedDocument(shareId, draft) }; vault.refreshShares() } else vault.saveItem(draft)
                val old = existing?.file?.key
                if (shareId == null && old != null && (removeFile || p != null) && old != file?.key) runCatchingSafe { api.deleteVaultFile(old) }
                notify(if (isNew) "${section.singular.replaceFirstChar { it.uppercase() }} saved." else "Changes saved.", false)
                closeEditor()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Could not save.", true) } finally { saving = false; progress = null }
        }
    }

    fun changeAddDocumentDestination() {
        val flow = EditorDrawer.addDocumentDraft ?: return
        val values = metadata.toMutableMap().apply { put("title", title); put("additionalData", metadata["additionalData"].orEmpty()) }
        EditorDrawer.openAddDocumentForChange(flow.copy(file = picked ?: flow.file, values = values))
    }

    fun runScan(retry: Boolean) {
        if (!canUpload) { notify("Smart Scan uploads require an active paid plan.", true); return }
        val p = picked ?: return
        val requestedSchema = scanSchemaSignature
        if (initialFile?.uri == p.uri) {
            autoScannedUri = p.uri
            lastAutoScanSchema = requestedSchema
        }
        scanning = true
        scope.launch {
            try {
                val defs = scanFieldDefinitions.map { SmartScanFieldDefinition(it.key, it.label, it.kind.name.lowercase(), it.options.takeIf { o -> o.isNotEmpty() }) }
                val result = withContext(Dispatchers.IO) { api.smartScan(p.name, p.mime, p.size, { p.open(context) }, sectionId, defs, retry) }
                scan = result
                val previousAutoValues = autoAppliedScanValues
                val appliedValues = previousAutoValues.toMutableMap()
                val firstAppliedScan = !autoScanAppliedOnce
                val destinationKeys = setOf("type", "accountKind", "financeType", "materialType", "relationship", "cardType", "urlCategory", "recordType")
                result.fields.forEach { (key, extracted) ->
                    if (extracted.value.isBlank() || scanFieldDefinitions.none { it.key == key } || key in userEditedScanFields) return@forEach
                    val existingValue = if (key == "title") title else metadata[key].orEmpty()
                    val selectedDestinationValue = key in destinationKeys && !initialMetadata[key].isNullOrBlank() && existingValue == initialMetadata[key]
                    if (selectedDestinationValue) return@forEach
                    val mayUpdate = firstAppliedScan || existingValue.isBlank() || previousAutoValues[key] == existingValue ||
                        (key in setOf("title", "additionalData") && initialMetadata[key] != null && existingValue == initialMetadata[key])
                    if (!mayUpdate) return@forEach
                    when {
                        key == "title" -> { title = extracted.value; appliedValues[key] = extracted.value }
                        sectionId == "documents" && key == "member" -> {
                            val suggested = extracted.value.trim()
                            val matched = if (suggested.equals("Me", ignoreCase = true)) "me" else familyMembers.firstOrNull { it.title.equals(suggested, ignoreCase = true) }?.id
                            if (matched != null) { set(key, matched); appliedValues[key] = matched }
                        }
                        else -> { val value = extracted.value.take(5000); set(key, value); appliedValues[key] = value }
                    }
                }
                if (sectionId == "documents" && result.documentType.isNotBlank() && "type" !in userEditedScanFields) {
                    val options = formFields.firstOrNull { it.key == "type" }?.options.orEmpty()
                    val currentType = metadata["type"] ?: initialMetadata["type"].orEmpty()
                    val mayUpdateType = currentType.isBlank() || initialMetadata["type"] == currentType || previousAutoValues["type"] == currentType
                    if (mayUpdateType) options.firstOrNull { it.equals(result.documentType, ignoreCase = true) }?.let { matched ->
                        set("type", matched)
                        appliedValues["type"] = matched
                    }
                }
                autoAppliedScanValues = appliedValues
                autoScanAppliedOnce = true
                if (initialFile?.uri == p.uri) lastAutoScanSchema = requestedSchema
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Smart scan failed.", true) } finally { scanning = false }
        }
    }

    LaunchedEffect(initialFile?.uri, picked?.uri, sectionId, canUpload, initialScanComplete, scanSchemaSignature, scanning, autoScanAppliedOnce) {
        val initial = initialFile ?: return@LaunchedEffect
        if (picked?.uri != initial.uri || sectionId == "wallet-cards" || !canUpload || !supportsSmartScanFile(initial) || scanning) return@LaunchedEffect
        val canAutoFill = !initialScanComplete || autoScanAppliedOnce
        if (!canAutoFill) return@LaunchedEffect
        if (autoScannedUri != initial.uri) {
            autoScannedUri = initial.uri
            lastAutoScanSchema = scanSchemaSignature
            runScan(false)
        } else if (lastAutoScanSchema != scanSchemaSignature) {
            lastAutoScanSchema = scanSchemaSignature
            runScan(false)
        }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = closeEditor) { Icon(Icons.Outlined.Close, "Cancel", tint = Bento.mutedFg) }
            Column(Modifier.weight(1f)) { Eyebrow(section.eyebrow); Text(if (isNew) "New ${noteKindLabel(sectionId, recordType, section.singular)}" else "Edit ${noteKindLabel(sectionId, recordType, section.singular)}", style = MaterialTheme.typography.titleMedium, color = Bento.fg) }
            if (initialFile != null && picked != null && EditorDrawer.addDocumentDraft != null) TextButton(onClick = ::changeAddDocumentDestination) { Text("Change space", color = Bento.primary) }
            PrimaryButton(if (progress != null) "Uploading ${progress}%" else if (saving) "Saving…" else "Save", onClick = ::save, enabled = !saving, loading = saving)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sectionId == "notes" && isNew) SegmentedTabs(listOf("Note", "Task", "Reminder", "Alarm"), listOf("note", "todo", "reminder", "alarm").indexOf(recordType).coerceAtLeast(0), { i -> val k = listOf("note", "todo", "reminder", "alarm")[i]; metadata = metadata + ("recordType" to k) + (if (k == "reminder" || k == "alarm") mapOf("enabled" to (metadata["enabled"] ?: "true"), "ringtoneId" to (metadata["ringtoneId"] ?: BUILTIN_RINGTONES.first().id), "ringtoneName" to (metadata["ringtoneName"] ?: BUILTIN_RINGTONES.first().name)) else emptyMap()) })

            BentoCard {
                TextInput(title, { userEditedScanFields = userEditedScanFields + "title"; title = it; errors = errors - "title" }, if (isWallet) "Cardholder name" else section.titleLabel, required = true, isError = errors["title"] != null, supporting = errors["title"])
                metadata["addFlowType"]?.takeIf { it.isNotBlank() }?.let { typeLabel ->
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Selected type", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg, modifier = Modifier.weight(1f))
                        Pill(typeLabel, Tones.Blue)
                    }
                }

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

                val visibleFields = formFields.filter { f -> f.key !in setOf("title", "lastFour", "expiryMonth", "expiryYear", "cardholder", "network", "cardNumber", "expiry") && (sectionId != "notes" || f.key in visibleNoteKeys(recordType)) }
                if (sectionId == "notes" && recordType != "note") { Spacer(Modifier.height(10.dp)); TextInput(metadata["todoDetails"].orEmpty(), { setFromUser("todoDetails", it) }, "Details", placeholder = "Anything you want to remember", minLines = 3) }
                if (recordType == "todo") { Spacer(Modifier.height(10.dp)); DateInput(metadata["dueDate"].orEmpty(), { setFromUser("dueDate", it) }, "Due date") }
                visibleFields.forEach { f ->
                    Spacer(Modifier.height(10.dp))
                    val v = metadata[f.key].orEmpty()
                    val err = errors[f.key]
                    when (f.kind) {
                        FieldKind.SELECT -> if (f.key == "member") {
                            val options = (listOf("me") + familyMembers.map { it.id } + listOfNotNull(v.takeIf { it.isNotBlank() })).distinct()
                            val labels = buildMap {
                                put("me", "Me")
                                familyMembers.forEach { member -> put(member.id, "${member.title}${member.metadata["relationship"]?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}") }
                                if (v.isNotBlank() && v != "me" && familyMembers.none { it.id == v }) put(v, if (v.length >= 30 && v.contains('-')) "Previously saved family member" else v)
                            }
                            val selected = when { v.equals("Me", true) -> "me"; familyMembers.any { it.title.equals(v, true) } -> familyMembers.first { it.title.equals(v, true) }.id; else -> v }
                            SelectInput(selected, options, { setFromUser(f.key, it) }, f.label, required = f.required, optionLabels = labels)
                        } else SelectInput(v, f.options, { setFromUser(f.key, it) }, f.label, required = f.required)
                        FieldKind.DATE -> DateInput(v, { setFromUser(f.key, it) }, f.label, required = f.required)
                        FieldKind.TEXTAREA -> TextInput(v, { setFromUser(f.key, it) }, f.label, placeholder = f.placeholder, minLines = 4, required = f.required, isError = err != null, supporting = err)
                        FieldKind.NUMBER -> TextInput(v, { setFromUser(f.key, it) }, f.label, placeholder = f.placeholder, keyboard = KeyboardType.Decimal, required = f.required, isError = err != null, supporting = err)
                        FieldKind.EMAIL -> TextInput(v, { setFromUser(f.key, it) }, f.label, placeholder = f.placeholder, keyboard = KeyboardType.Email, required = f.required, isError = err != null, supporting = err)
                        FieldKind.URL -> TextInput(v, { setFromUser(f.key, it) }, f.label, placeholder = f.placeholder, keyboard = KeyboardType.Uri, required = f.required, isError = err != null, supporting = err)
                        else -> TextInput(v, { setFromUser(f.key, it) }, f.label, placeholder = f.placeholder, required = f.required, isError = err != null, supporting = err)
                    }
                }

                if (initialFile != null && sectionId !in setOf("notes", "wallet-cards") && formFields.none { it.key == "additionalData" }) {
                    Spacer(Modifier.height(10.dp))
                    TextInput(metadata["additionalData"].orEmpty(), { setFromUser("additionalData", it.take(5000)) }, "Additional Data", placeholder = "Other extracted information that does not fit the fields above", minLines = 3)
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

            if (sectionId != "wallet-cards") BentoCard {
                SectionHeading("Attachment", if (existing?.file != null && !removeFile && picked == null) "Current file" else "Add a file")
                val current = picked?.let { it.name to Files.formatSize(it.size) } ?: existing?.file?.takeIf { !removeFile }?.let { it.name to Files.formatSize(it.size) }
                if (current != null) Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToneIconBox(Icons.Outlined.AttachFile, Tones.Blue, size = 34.dp, radius = 10.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(current.first, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(current.second, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
                    IconButton(onClick = { if (picked != null) picked = null else removeFile = true }) { Icon(Icons.Outlined.Delete, "Remove", tint = Bento.danger) }
                } else Text("PDFs, images and documents up to 25 MB are stored privately in your vault.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                if (!canUpload) Text("New uploads need an active paid plan. You can still edit this record; any existing file stays accessible unless you choose to remove it.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Choose file", onClick = { filePicker.launch(arrayOf("*/*")) }, icon = Icons.Outlined.UploadFile, enabled = canUpload)
                    QuietButton("Take photo", onClick = { val uri = Files.newCameraUri(context); cameraUri = uri; camera.launch(uri) }, icon = Icons.Outlined.PhotoCamera, enabled = canUpload)
                }
                if (scanFieldDefinitions.isNotEmpty() && sectionId != "wallet-cards") {
                    Spacer(Modifier.height(12.dp))
                    HairLine()
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToneIconBox(Icons.Outlined.DocumentScanner, Tones.Violet, size = 34.dp); Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Smart Scan", style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                            Text(if (picked == null) "Attach a photo or PDF to suggest field values." else "Scan ${picked!!.name} and review every suggestion.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (picked != null && canUpload) SoftButton(if (scanning) "Scanning…" else if (scan != null) "Scan again" else "Scan", onClick = { if (!scanning) runScan(scan != null) }, icon = Icons.Outlined.AutoAwesome, enabled = !scanning)
                    }
                    if (!canUpload) Text("Smart Scan requires an active paid plan.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(start = 43.dp, top = 5.dp))
                    scan?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        if (result.documentType.isNotBlank()) Pill("Looks like: ${result.documentType} · ${result.documentTypeConfidence}", Tones.Violet)
                        result.fields.filter { it.value.value.isNotBlank() }.forEach { (key, r) ->
                            val label = formFields.firstOrNull { it.key == key }?.label ?: key
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg); Text(r.value, style = MaterialTheme.typography.bodyMedium, color = Bento.fg, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Pill(r.confidence, if (r.confidence == "high") Tones.Green else if (r.confidence == "medium") Tones.Amber else Tones.Neutral)
                                if (!autoFillFileFlow) TextButton(onClick = { if (key == "title") { userEditedScanFields = userEditedScanFields + "title"; title = r.value } else setFromUser(key, r.value) }) { Text("Use", color = Bento.primary) }
                            }
                        }
                        result.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Accents.amber.text) }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!autoFillFileFlow) QuietButton("Apply all", onClick = { result.fields.forEach { (key, value) -> if (value.value.isNotBlank()) { if (key == "title") { userEditedScanFields = userEditedScanFields + "title"; title = value.value } else setFromUser(key, value.value) } } })
                            if (autoFillFileFlow) Text("Matching values are filled automatically; unmatched information stays in Additional Data.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.weight(1f))
                            TextButton(onClick = { runScan(true) }) { Text("Re-scan", color = Bento.mutedFg) }
                        }
                    }
                }
            }

            Text("Saved to the same Persora vault you use on the web. Section: ${section.label}.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
        }
    }
}

private fun noteKindLabel(sectionId: String, recordType: String, fallback: String) = if (sectionId != "notes") fallback else when (recordType) { "todo" -> "task"; "reminder" -> "reminder"; "alarm" -> "alarm"; else -> "note" }

private fun walletExpiry(metadata: Map<String, String>?): String = metadata?.let { values ->
    Cards.savedExpiry(values["expiryMonth"], values["expiryYear"]).ifBlank { values["expiry"].orEmpty() }
}.orEmpty()

/** Which section-field keys apply to each notes record type (mirrors the conditional form in VaultDialogs.tsx). */
private fun visibleNoteKeys(recordType: String): Set<String> = when (recordType) {
    "todo" -> setOf("todoDetails", "dueDate", "tags", "additionalData")
    "reminder" -> setOf("todoDetails", "reminderAt", "tags", "additionalData")
    "alarm" -> setOf("todoDetails", "alarmTime", "alarmDate", "repeatDays", "tags", "additionalData")
    else -> setOf("content", "tags", "category", "additionalData")
}
