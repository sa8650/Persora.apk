package app.persora.android.ui.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.persora.android.appContainer
import app.persora.android.core.util.Files
import app.persora.android.core.util.PickedFile
import app.persora.android.core.util.runCatchingSafe
import app.persora.android.data.model.FieldDefinition
import app.persora.android.data.model.MEDICAL_RECORD_TYPES
import app.persora.android.data.model.SmartScanFieldDefinition
import app.persora.android.data.model.SmartScanResult
import app.persora.android.data.model.SectionDefinition
import app.persora.android.data.model.Sections
import app.persora.android.ui.components.BentoCard
import app.persora.android.ui.components.Eyebrow
import app.persora.android.ui.components.Pill
import app.persora.android.ui.components.PrimaryButton
import app.persora.android.ui.components.QuietButton
import app.persora.android.ui.components.SectionHeading
import app.persora.android.ui.components.SelectInput
import app.persora.android.ui.components.SoftButton
import app.persora.android.ui.components.ToneIconBox
import app.persora.android.ui.components.observeCurrentUser
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val MAX_ADD_UPLOAD_BYTES = 25L * 1024 * 1024
private const val MAX_ADD_SCAN_BYTES = 7L * 1024 * 1024

private data class AddDocumentType(val label: String, val value: String = label)
private enum class AddDocumentSpaceKind { SECTION, CONTACTS, MEDICAL_RECORDS, BUSINESS_CARDS }
private data class AddDocumentSpace(
    val id: String,
    val label: String,
    val eyebrow: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: String,
    val types: List<AddDocumentType>,
    val typeField: String? = null,
    val kind: AddDocumentSpaceKind = AddDocumentSpaceKind.SECTION,
    val section: SectionDefinition? = null,
)

private fun selectableTypes(field: FieldDefinition?): List<AddDocumentType> =
    field?.options.orEmpty().map { AddDocumentType(it) }

private fun sectionTypeConfig(section: SectionDefinition): Pair<String?, List<AddDocumentType>> = when (section.id) {
    "documents" -> "type" to selectableTypes(section.fields.firstOrNull { it.key == "type" })
    "academics" -> "type" to selectableTypes(section.fields.firstOrNull { it.key == "type" })
    "subscriptions" -> null to listOf(
        AddDocumentType("Streaming & entertainment"), AddDocumentType("Software & cloud"),
        AddDocumentType("Membership"), AddDocumentType("Other subscription"),
    )
    "family" -> "relationship" to selectableTypes(section.fields.firstOrNull { it.key == "relationship" })
    "purchases" -> null to listOf(
        AddDocumentType("Receipt / invoice"), AddDocumentType("Warranty record"), AddDocumentType("Purchase record"),
    )
    "accounts" -> "accountKind" to selectableTypes(section.fields.firstOrNull { it.key == "accountKind" })
    "personal-finance" -> "financeType" to selectableTypes(section.fields.firstOrNull { it.key == "financeType" })
        .map { it.copy(label = it.value.replaceFirstChar(Char::uppercase)) }
    "memberships" -> "type" to selectableTypes(section.fields.firstOrNull { it.key == "type" })
    "wallet-cards" -> "cardType" to selectableTypes(section.fields.firstOrNull { it.key == "cardType" })
    "study" -> "materialType" to selectableTypes(section.fields.firstOrNull { it.key == "materialType" })
    "notes" -> "recordType" to listOf(
        AddDocumentType("Note", "note"), AddDocumentType("Task", "todo"),
        AddDocumentType("Reminder", "reminder"), AddDocumentType("Alarm", "alarm"),
    )
    "urls" -> "urlCategory" to selectableTypes(section.fields.firstOrNull { it.key == "urlCategory" })
    else -> null to listOf(AddDocumentType(section.singular.replaceFirstChar(Char::uppercase)))
}

private val ADD_DOCUMENT_SPACES: List<AddDocumentSpace> = buildList {
    Sections.all.forEach { section ->
        if (section.id == "business-card") {
            add(AddDocumentSpace(
                id = "business-cards", label = "Business cards", eyebrow = "Your digital identity",
                icon = Icons.Outlined.BusinessCenter, color = "slate",
                types = listOf(AddDocumentType("Digital business card")), kind = AddDocumentSpaceKind.BUSINESS_CARDS,
            ))
        } else {
            val (field, types) = sectionTypeConfig(section)
            add(AddDocumentSpace(section.id, section.label, section.eyebrow, section.icon, section.color, types, field, section = section))
        }
    }
    add(AddDocumentSpace(
        id = "contacts", label = "Contacts", eyebrow = "People, organized",
        icon = Icons.Outlined.ContactPage, color = "teal",
        types = app.persora.android.data.model.CONTACT_CATEGORIES.map { AddDocumentType(it) }, kind = AddDocumentSpaceKind.CONTACTS,
    ))
    add(AddDocumentSpace(
        id = "medical-records", label = "Medical records", eyebrow = "Private health archive",
        icon = Icons.Outlined.MonitorHeart, color = "red",
        types = MEDICAL_RECORD_TYPES.map { AddDocumentType(it) }, kind = AddDocumentSpaceKind.MEDICAL_RECORDS,
    ))
}

private fun fileUploadSpaces(spaces: List<AddDocumentSpace>): List<AddDocumentSpace> = spaces
    .filter { (it.kind == AddDocumentSpaceKind.SECTION && it.id != "wallet-cards") || it.kind == AddDocumentSpaceKind.MEDICAL_RECORDS }
    .map { space -> if (space.id == "notes") space.copy(types = space.types.filter { it.value == "note" }) else space }

private fun normalizedType(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

private fun otherType(space: AddDocumentSpace): AddDocumentType? =
    space.types.firstOrNull { normalizedType(it.value) in setOf("other", "others") || normalizedType(it.label) in setOf("other", "others") }

private fun matchSuggestedType(space: AddDocumentSpace, suggestion: String): AddDocumentType? {
    if (suggestion.isBlank()) return null
    val normalized = normalizedType(suggestion)
    space.types.firstOrNull { normalizedType(it.label) == normalized || normalizedType(it.value) == normalized }?.let { return it }
    val pattern = when {
        "passport" in normalized -> Regex("passport", RegexOption.IGNORE_CASE)
        "nid" in normalized || "national id" in normalized || "identity card" in normalized -> Regex("nid|national id", RegexOption.IGNORE_CASE)
        "birth" in normalized -> Regex("birth certificate", RegexOption.IGNORE_CASE)
        "driving" in normalized || "licence" in normalized || "license" in normalized -> Regex("driving|licen", RegexOption.IGNORE_CASE)
        "visa" in normalized -> Regex("visa", RegexOption.IGNORE_CASE)
        "transcript" in normalized -> Regex("transcript", RegexOption.IGNORE_CASE)
        "mark sheet" in normalized -> Regex("mark sheet", RegexOption.IGNORE_CASE)
        "admission" in normalized -> Regex("admission", RegexOption.IGNORE_CASE)
        "prescription" in normalized -> Regex("prescription", RegexOption.IGNORE_CASE)
        "lab test" in normalized || "blood test" in normalized -> Regex("lab test", RegexOption.IGNORE_CASE)
        "imaging" in normalized || "x ray" in normalized || "scan" in normalized -> Regex("imaging|scan", RegexOption.IGNORE_CASE)
        "vaccin" in normalized -> Regex("vaccin", RegexOption.IGNORE_CASE)
        "discharge" in normalized -> Regex("discharge summary", RegexOption.IGNORE_CASE)
        "warranty" in normalized -> Regex("warranty", RegexOption.IGNORE_CASE)
        "receipt" in normalized || "invoice" in normalized -> Regex("receipt|invoice", RegexOption.IGNORE_CASE)
        "subscription" in normalized -> Regex("subscription", RegexOption.IGNORE_CASE)
        "bank statement" in normalized -> Regex("bank account", RegexOption.IGNORE_CASE)
        else -> null
    }
    pattern?.let { rule -> space.types.firstOrNull { rule.containsMatchIn(it.label) || rule.containsMatchIn(it.value) }?.let { return it } }
    val ignored = setOf("document", "record", "file", "card", "certificate", "official", "personal", "other")
    val tokens = normalized.split(" ").filter { it.length > 2 && it !in ignored }
    val best = space.types.map { option ->
        option to tokens.count { normalizedType("${option.label} ${option.value}").contains(it) }
    }.maxByOrNull { it.second }
    return best?.takeIf { it.second > 0 }?.first
}

private fun suggestedSpace(spaces: List<AddDocumentSpace>, result: SmartScanResult): AddDocumentSpace? {
    val predicted = result.fields["suggestedSpace"]?.value?.trim()
    spaces.firstOrNull { it.label.equals(predicted, ignoreCase = true) }?.let { return it }
    val matches = spaces.filter { matchSuggestedType(it, result.documentType) != null }
    return matches.singleOrNull()
}

private fun uploadTitle(fileName: String): String {
    val stem = fileName.substringBeforeLast('.', fileName)
    return stem.replace(Regex("[._-]+"), " ").replace(Regex("\\s+"), " ").trim().ifBlank { "Untitled document" }
}

private val ADD_DOCUMENT_DESTINATION_KEYS = setOf("type", "accountKind", "financeType", "materialType", "relationship", "cardType", "urlCategory", "recordType", "addFlowType")

private fun additionalDataWithCarry(existing: String, values: Map<String, String>): String {
    val carried = values.filterKeys { it !in ADD_DOCUMENT_DESTINATION_KEYS && it !in setOf("title", "additionalData") && it !in setOf("enabled", "ringtoneId", "ringtoneName", "completed", "favorite", "pinned") }
        .filterValues { it.isNotBlank() }
        .map { (key, value) -> "$key: $value" }
    return (listOf(existing) + carried).filter(String::isNotBlank).distinct().joinToString("\n\n")
}

private fun carriedSectionValues(space: AddDocumentSpace, type: AddDocumentType, values: Map<String, String>): Pair<Map<String, String>, String> {
    if (values.isEmpty() || space.kind != AddDocumentSpaceKind.SECTION) return emptyMap<String, String>() to additionalDataWithCarry(values["additionalData"].orEmpty(), values)
    val definitions = Sections.editorFields(
        space.id,
        type = type.value,
        accountKind = if (space.typeField == "accountKind") type.value else values["accountKind"].orEmpty(),
        financeType = if (space.typeField == "financeType") type.value else values["financeType"].orEmpty(),
        materialType = if (space.typeField == "materialType") type.value else values["materialType"].orEmpty(),
    )
    val allowedKeys = definitions.map { it.key }.toSet()
    val retained = values.filter { (key, value) -> key in allowedKeys && key !in ADD_DOCUMENT_DESTINATION_KEYS && key !in setOf("title", "additionalData") && value.isNotBlank() }
    val unmatched = values.filter { (key, value) -> key !in allowedKeys && key !in ADD_DOCUMENT_DESTINATION_KEYS && key !in setOf("title", "additionalData") && value.isNotBlank() }
    val additional = additionalDataWithCarry(values["additionalData"].orEmpty(), unmatched)
    return retained to additional
}

private fun supportedScanFile(file: PickedFile): Boolean {
    val mime = file.mime.lowercase(Locale.ROOT).substringBefore(';')
    val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return file.size in 1L..MAX_ADD_SCAN_BYTES && (
        mime in setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "image/gif", "image/tiff", "image/bmp") ||
            extension in setOf("pdf", "jpg", "jpeg", "png", "webp", "gif", "tif", "tiff", "bmp")
        )
}

/** App-wide file-first Add document flow; the destination-specific editor remains the final review/save step. */
@Composable
fun AddDocumentScreen(onClose: () -> Unit, initialDraft: AddDocumentFlowDraft? = null) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current
    val canUpload = observeCurrentUser()?.uploadsEnabled == true
    var remoteDocumentTypes by remember { mutableStateOf(emptyList<String>()) }
    val spaces = remember(remoteDocumentTypes) {
        ADD_DOCUMENT_SPACES.map { space ->
            when (space.id) {
                "documents" -> {
                    val types = if (remoteDocumentTypes.isEmpty()) space.types else (remoteDocumentTypes + listOf("CV / Resume", "Other")).distinct().map { AddDocumentType(it) }
                    space.copy(types = types.map { type -> if (normalizedType(type.value) == "other") type.copy(label = "Others") else type })
                }
                else -> space
            }
        }.map { space ->
            space.copy(types = space.types.map { type -> if (normalizedType(type.value) == "other") type.copy(label = "Others") else type })
        }
    }
    val fileSpaces = remember(spaces) { fileUploadSpaces(spaces) }
    var stage by remember(initialDraft) { mutableStateOf(if (initialDraft != null) "destination" else "choose") }
    var manualDestination by remember(initialDraft) { mutableStateOf(initialDraft != null && initialDraft.file == null) }
    var selectedFile by remember(initialDraft) { mutableStateOf(initialDraft?.file) }
    var selectedSpaceId by remember(initialDraft) { mutableStateOf(initialDraft?.spaceId.orEmpty()) }
    var selectedTypeValue by remember(initialDraft) { mutableStateOf(initialDraft?.typeValue.orEmpty()) }
    var handoffStarted by remember(initialDraft) { mutableStateOf(false) }
    var destinationSelectionTouched by remember(initialDraft) { mutableStateOf(initialDraft == null) }
    var scanResult by remember(initialDraft) { mutableStateOf(initialDraft?.scanResult) }
    var scanError by remember(initialDraft) { mutableStateOf(initialDraft?.scanError) }
    var scanning by remember { mutableStateOf(false) }
    var scanAttempted by remember(initialDraft) { mutableStateOf(initialDraft?.scanAttempted == true) }
    var previewShownUri by remember(initialDraft) { mutableStateOf(initialDraft?.file?.uri) }
    var documentTypesLoaded by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(previewShownUri, manualDestination) {
        if ((previewShownUri == null && !manualDestination) || documentTypesLoaded) return@LaunchedEffect
        documentTypesLoaded = true
        remoteDocumentTypes = withContext(Dispatchers.IO) {
            runCatching { container.api.loadDocumentTypes().filter { it.active }.sortedBy { it.sort_order }.map { it.name } }.getOrDefault(emptyList())
        }
    }
    val destinationSpaces = if (manualDestination) spaces else fileSpaces
    val selectedSpace = destinationSpaces.firstOrNull { it.id == selectedSpaceId }
    val selectedType = selectedSpace?.types?.firstOrNull { it.value == selectedTypeValue }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatchingSafe { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (!canUpload) {
            notify("New file uploads require an active paid plan.", true)
            return@rememberLauncherForActivityResult
        }
        val file = Files.describe(context, uri)
        if (file.size > MAX_ADD_UPLOAD_BYTES) {
            fileError = "Files must be 25 MB or smaller."
        } else {
            selectedFile = file
            manualDestination = false
            selectedSpaceId = ""
            selectedTypeValue = ""
            handoffStarted = false
            destinationSelectionTouched = true
            scanResult = null
            scanError = null
            scanAttempted = false
            previewShownUri = null
            fileError = null
            stage = "preview"
        }
    }

    fun selectSpace(id: String) {
        destinationSelectionTouched = true
        handoffStarted = false
        selectedSpaceId = id
        val next = destinationSpaces.firstOrNull { it.id == id }
        selectedTypeValue = if (next != null && scanResult != null) {
            (matchSuggestedType(next, scanResult!!.documentType) ?: otherType(next))?.value.orEmpty()
        } else ""
    }

    fun runClassificationScan(retry: Boolean = false) {
        val file = selectedFile ?: return
        if (scanning || (scanAttempted && !retry)) return
        if (!canUpload || !supportedScanFile(file)) {
            scanResult = null
            scanError = if (!canUpload) "Smart Scan uploads require an active paid plan." else "Smart Scan supports PDF and common image files up to 7 MB. Choose a destination manually."
            stage = "destination"
            return
        }
        scanAttempted = true
        scanning = true
        scanError = null
        scope.launch {
            try {
                val definitions = listOf(
                    SmartScanFieldDefinition("suggestedSpace", "Best-fit Persora space", "select", fileSpaces.map { it.label }),
                    SmartScanFieldDefinition("additionalData", "Additional Data", "textarea"),
                )
                val result = withContext(Dispatchers.IO) {
                    container.api.smartScan(file.name, file.mime, file.size, { file.open(context) }, "documents", definitions, retry)
                }
                scanResult = result
                val documentsSpace = fileSpaces.firstOrNull { it.id == "documents" }
                val recommended = suggestedSpace(fileSpaces, result) ?: documentsSpace
                val suggestedType = recommended?.let { matchSuggestedType(it, result.documentType) ?: otherType(it) }
                selectedSpaceId = recommended?.id.orEmpty()
                selectedTypeValue = suggestedType?.value.orEmpty()
                // A successful response with no confident classification still moves forward using the requested safe defaults.
                scanError = null
                stage = "destination"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                scanResult = null
                scanError = "Smart Scan couldn't read this file. Choose a Space and Document Type manually."
                selectedSpaceId = ""
                selectedTypeValue = ""
                stage = "destination"
            } finally {
                scanning = false
            }
        }
    }

    LaunchedEffect(stage, selectedFile?.uri, previewShownUri, canUpload, scanAttempted) {
        val file = selectedFile ?: return@LaunchedEffect
        if (stage == "preview" && previewShownUri == file.uri && !scanAttempted && canUpload && supportedScanFile(file)) {
            runClassificationScan(false)
        }
    }

    fun continueToFields() {
        val space = selectedSpace ?: return
        val type = selectedType ?: return
        val file = selectedFile
        val carriedValues = initialDraft?.values.orEmpty()
        val (carriedMetadata, carriedAdditionalData) = carriedSectionValues(space, type, carriedValues)
        val scanAdditionalData = scanResult?.fields?.get("additionalData")?.value.orEmpty()
        val additionalData = (listOf(carriedAdditionalData, scanAdditionalData)).filter { it.isNotBlank() }.distinct().joinToString("\n\n")
        val title = carriedValues["title"]?.takeIf { it.isNotBlank() } ?: file?.let { uploadTitle(it.name) }.orEmpty()
        // A successful classification scan is not the destination-specific field extraction. Let the existing editor
        // re-use the cached OCR and fill its own schema automatically; only suppress that pass after a scan failure.
        val initialScanComplete = file != null && (scanError != null || scanResult == null)
        val metadata = buildMap {
            putAll(carriedMetadata)
            space.typeField?.let { put(it, type.value) }
            if (space.typeField == null) put("addFlowType", type.label)
            if (file != null) {
                put("title", title)
                put("additionalData", additionalData)
            }
        }
        if (file != null) {
            EditorDrawer.rememberAddDocumentDraft(
                AddDocumentFlowDraft(file, space.id, type.value, scanResult, scanError, scanAttempted, metadata),
            )
        }
        when (space.kind) {
            AddDocumentSpaceKind.SECTION -> EditorDrawer.openItem(
                space.id,
                kind = type.value.takeIf { space.id == "notes" },
                initialMetadata = metadata,
                initialFile = file,
                initialScanResult = scanResult.takeIf { scanError == null },
                initialScanComplete = initialScanComplete,
            )
            AddDocumentSpaceKind.CONTACTS -> EditorDrawer.openContact(initialCategory = type.value)
            AddDocumentSpaceKind.MEDICAL_RECORDS -> EditorDrawer.openMedical(
                initialType = type.value,
                initialFile = file,
                initialTitle = title,
                initialAdditionalData = additionalData,
                initialScanResult = scanResult.takeIf { scanError == null },
                initialScanComplete = initialScanComplete,
            )
            AddDocumentSpaceKind.BUSINESS_CARDS -> EditorDrawer.openBusinessCard()
        }
    }

    LaunchedEffect(stage, selectedSpaceId, selectedTypeValue, scanning, scanAttempted, manualDestination, selectedFile?.uri, scanError, destinationSelectionTouched) {
        if (stage != "destination" || scanning || handoffStarted || selectedSpace == null || selectedType == null) return@LaunchedEffect
        if (initialDraft != null && !destinationSelectionTouched) return@LaunchedEffect
        val readyToOpen = manualDestination || selectedFile == null || scanAttempted || scanError != null
        if (!readyToOpen) return@LaunchedEffect
        handoffStarted = true
        continueToFields()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = Bento.mutedFg) }
                Column(Modifier.weight(1f)) {
                    Eyebrow("New vault entry")
                    Text("Add document", style = MaterialTheme.typography.titleMedium, color = Bento.fg)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (stage == "choose") {
                BentoCard {
                    SectionHeading("Choose how to start", "NEW VAULT ENTRY")
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Choose File", onClick = { filePicker.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.fillMaxWidth(), enabled = canUpload)
                    Spacer(Modifier.height(8.dp))
                    SoftButton("Scan & Upload · Coming soon", onClick = {}, icon = Icons.Outlined.AutoAwesome, enabled = false)
                    Text("Choose File previews the original on this phone before any OCR upload.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.padding(top = 6.dp))
                    if (!canUpload) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = Bento.subtleFg, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("New file uploads require an active paid plan.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
                    }
                    fileError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Bento.danger, modifier = Modifier.padding(top = 6.dp)) }
                }
                TextButton(onClick = {
                    selectedFile = null
                    scanResult = null
                    scanError = null
                    scanAttempted = false
                    previewShownUri = null
                    selectedSpaceId = ""
                    selectedTypeValue = ""
                    manualDestination = true
                    stage = "destination"
                }) { Text("Add a record without a file", color = Bento.primary) }
            }

            if (stage == "preview" && selectedFile != null) {
                BentoCard {
                    SectionHeading("Preview before upload", "LOCAL FILE PREVIEW")
                    AddDocumentLocalPreview(selectedFile!!) { shownUri -> previewShownUri = shownUri }
                    Spacer(Modifier.height(8.dp))
                    Text("The original stays on this phone until final Save. Smart Scan reads a transient upload and does not add the attachment to your vault.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                    if (!supportedScanFile(selectedFile!!)) {
                        Text("Smart Scan supports PDF and common image files up to 7 MB. You can still attach this file and choose a destination.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
                        PrimaryButton("Choose Space & Type", onClick = { scanError = "Smart Scan isn't available for this file. Choose manually."; stage = "destination" }, modifier = Modifier.fillMaxWidth())
                    } else {
                        if (!scanAttempted) {
                            PrimaryButton("Scan now with Smart Scan", onClick = { runClassificationScan(false) }, modifier = Modifier.fillMaxWidth(), enabled = !scanning && canUpload)
                            Text("Smart Scan starts automatically as soon as this local preview is shown.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Bento.primary)
                                Text(if (scanning) "Smart Scan OCR is running. The vault copy waits until you Save." else "Smart Scan finished.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                            }
                        }
                    }
                    SoftButton("Choose a different file", onClick = { filePicker.launch(arrayOf("application/pdf", "image/*")) }, icon = Icons.Outlined.UploadFile, enabled = canUpload && !scanning)
                    if (!canUpload) Text("New file uploads and Smart Scan require an active paid plan.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
                    fileError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Bento.danger) }
                    QuietButton("Cancel", onClick = onClose)
                }
            }

            if (stage == "destination") {
                if (!manualDestination && scanResult != null && scanError == null) {
                    BentoCard {
                        SectionHeading("Smart Scan suggestions", "REVIEW & CHANGE")
                        scanResult?.documentType?.takeIf { it.isNotBlank() }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Detected document", style = MaterialTheme.typography.labelSmall, color = Bento.subtleFg); Text(it, style = MaterialTheme.typography.titleSmall, color = Bento.fg) }
                                Pill(scanResult?.documentTypeConfidence ?: "low", Tones.Violet)
                            }
                        }
                        scanResult?.fields?.get("suggestedSpace")?.value?.takeIf { it.isNotBlank() }?.let {
                            Text("Suggested space: $it · You can change both selectors below.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                if (!manualDestination && scanError != null) {
                    Text("Smart Scan couldn't read enough to make suggestions. Choose only a Space and Document Type to continue.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(horizontal = 2.dp))
                }
                BentoCard {
                    SectionHeading("Choose a Space", if (manualDestination) "WHERE IT BELONGS" else "SELECT DESTINATION")
                    Spacer(Modifier.height(4.dp))
                    SelectInput(
                        selectedSpace?.label ?: "Choose a space",
                        destinationSpaces.map { it.label },
                        { label -> selectSpace(destinationSpaces.firstOrNull { it.label == label }?.id.orEmpty()) },
                        "Space",
                        allowEmpty = false,
                    )
                    if (selectedSpace != null) {
                        Spacer(Modifier.height(10.dp))
                        SelectInput(
                            selectedType?.label ?: "Choose a document type",
                            selectedSpace.types.map { it.label },
                            { label -> destinationSelectionTouched = true; handoffStarted = false; selectedTypeValue = selectedSpace.types.firstOrNull { it.label == label }?.value.orEmpty() },
                            "Document Type",
                            allowEmpty = false,
                        )
                    }
                }
                Text("The matching fields open automatically after both selectors are set.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.padding(horizontal = 2.dp))
                QuietButton("Back", onClick = {
                    if (selectedFile != null) {
                        manualDestination = false
                        stage = "preview"
                    } else {
                        manualDestination = false
                        stage = "choose"
                    }
                })
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 2.dp)) {
                Icon(Icons.Outlined.Description, null, tint = Bento.subtleFg, modifier = Modifier.size(16.dp))
                Text("Your finished entry is saved to the selected space in your private vault.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
            }
        }
    }
}

@Composable
private fun AddDocumentLocalPreview(file: PickedFile, onPreviewShown: (Uri) -> Unit) {
    val context = LocalContext.current
    var image by remember(file.uri) { mutableStateOf<ImageBitmap?>(null) }
    var resolved by remember(file.uri) { mutableStateOf(false) }
    LaunchedEffect(file.uri) {
        val bitmap = withContext(Dispatchers.IO) { runCatching { renderLocalPreview(context, file) }.getOrNull() }
        image = bitmap?.asImageBitmap()
        resolved = true
        withFrameNanos { _ -> }
        onPreviewShown(file.uri)
    }
    BentoCard(padding = 10.dp) {
        Box(
            Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFF1F5FA)).border(1.dp, Bento.border, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                image != null -> Image(image!!, contentDescription = "Preview of ${file.name}", modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                !resolved -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Bento.primary)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(18.dp)) {
                    Icon(Icons.Outlined.Description, null, tint = Bento.primary, modifier = Modifier.size(30.dp))
                    Text("Inline preview unavailable", style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                    Text("The file is still local. You can continue and attach it after review.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(file.name, style = MaterialTheme.typography.labelLarge, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${Files.formatSize(file.size)} · Local preview", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
    }
}

private fun renderLocalPreview(context: Context, file: PickedFile): Bitmap? {
    if (Files.isPdf(file.mime, file.name)) {
        val descriptor = context.contentResolver.openFileDescriptor(file.uri, "r") ?: return null
        descriptor.use { parcel ->
            PdfRenderer(parcel).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val width = page.width.coerceAtMost(1200).coerceAtLeast(1)
                    val height = (page.height.toFloat() * width / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    val matrix = Matrix().apply { setScale(width.toFloat() / page.width, height.toFloat() / page.height) }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
    if (!Files.isImage(file.mime)) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    file.open(context).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1800) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
    return file.open(context).use { BitmapFactory.decodeStream(it, null, options) }
}
