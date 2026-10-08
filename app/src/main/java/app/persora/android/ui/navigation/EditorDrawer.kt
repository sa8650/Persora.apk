package app.persora.android.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.persora.android.core.util.PickedFile
import app.persora.android.data.model.SmartScanResult

/** Central editor drawer state so create/edit forms keep the current workspace visible underneath. */
sealed interface EditorTarget {
    data class Item(val sectionId: String, val itemId: String? = null, val folderId: String? = null, val kind: String? = null, val shareId: String? = null, val initialMetadata: Map<String, String> = emptyMap(), val initialFile: PickedFile? = null, val initialScanResult: SmartScanResult? = null, val initialScanComplete: Boolean = false) : EditorTarget
    data class Contact(val id: String? = null, val initialCategory: String? = null) : EditorTarget
    data class Medical(val id: String? = null, val initialType: String? = null, val initialFile: PickedFile? = null, val initialTitle: String = "", val initialAdditionalData: String = "", val initialScanResult: SmartScanResult? = null, val initialScanComplete: Boolean = false) : EditorTarget
    data class BusinessCard(val id: String? = null) : EditorTarget
    data class AddDocument(val draft: AddDocumentFlowDraft? = null) : EditorTarget
}

data class AddDocumentFlowDraft(
    val file: PickedFile?,
    val spaceId: String,
    val typeValue: String,
    val scanResult: SmartScanResult?,
    val scanError: String? = null,
    val scanAttempted: Boolean = false,
    val values: Map<String, String> = emptyMap(),
)

object EditorDrawer {
    var target by mutableStateOf<EditorTarget?>(null)
        private set
    var addDocumentDraft by mutableStateOf<AddDocumentFlowDraft?>(null)
        private set

    fun openItem(sectionId: String, itemId: String? = null, folderId: String? = null, kind: String? = null, shareId: String? = null, initialMetadata: Map<String, String> = emptyMap(), initialFile: PickedFile? = null, initialScanResult: SmartScanResult? = null, initialScanComplete: Boolean = false) {
        Details.close()
        target = EditorTarget.Item(sectionId, itemId, folderId, kind, shareId, initialMetadata, initialFile, initialScanResult, initialScanComplete)
    }
    fun openContact(id: String? = null, initialCategory: String? = null) { Details.close(); target = EditorTarget.Contact(id, initialCategory) }
    fun openMedical(id: String? = null, initialType: String? = null, initialFile: PickedFile? = null, initialTitle: String = "", initialAdditionalData: String = "", initialScanResult: SmartScanResult? = null, initialScanComplete: Boolean = false) { Details.close(); target = EditorTarget.Medical(id, initialType, initialFile, initialTitle, initialAdditionalData, initialScanResult, initialScanComplete) }
    fun openBusinessCard(id: String? = null) { Details.close(); target = EditorTarget.BusinessCard(id) }
    fun rememberAddDocumentDraft(draft: AddDocumentFlowDraft) { addDocumentDraft = draft }
    fun openAddDocument() { Details.close(); addDocumentDraft = null; target = EditorTarget.AddDocument() }
    fun openAddDocumentForChange(draft: AddDocumentFlowDraft) { Details.close(); addDocumentDraft = draft; target = EditorTarget.AddDocument(draft) }
    fun close() { target = null; addDocumentDraft = null }
}
