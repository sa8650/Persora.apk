package app.persora.android.data.model

import kotlinx.serialization.Serializable

/* ---------- Identity ---------- */

@Serializable
data class AppUser(
    val id: String,
    val userId: String = "",          // public seven-digit Persora ID
    val email: String = "",
    val fullName: String = "",
    val role: String = "user",        // "user" | "admin"
    val timezone: String = "Asia/Dhaka",
    val avatarUrl: String = "",
    val emailVerified: Boolean = false,
    val uploadsEnabled: Boolean = false,
) {
    val initials: String get() = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "P" }
}

/* ---------- Vault ---------- */

@Serializable
data class VaultFile(val key: String? = null, val name: String, val size: Long? = null, val type: String? = null)

@Serializable
data class SharedItemAccess(val shareId: String, val permission: String, val direction: String, val owner: ShareUser, val recipient: ShareUser)

@Serializable
data class VaultItem(
    val id: String,
    val section: String,
    val title: String,
    val subtitle: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: String,
    val updatedAt: String,
    val file: VaultFile? = null,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
    val folderId: String? = null,
    val sharedAccess: SharedItemAccess? = null,
) {
    val recordType: String get() = metadata["recordType"] ?: ""
    val isTodo: Boolean get() = section == "notes" && recordType == "todo"
    val isReminder: Boolean get() = section == "notes" && recordType == "reminder"
    val isAlarm: Boolean get() = section == "notes" && recordType == "alarm"
    val isSchedule: Boolean get() = isReminder || isAlarm
}

@Serializable
data class VaultFolder(
    val id: String,
    val scope: String,
    val name: String,
    val color: String = "blue",
    val pinned: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/* ---------- Contacts ---------- */

val CONTACT_CATEGORIES = listOf("Family", "Friends", "Work", "Clients", "Suppliers", "Students", "Other")

@Serializable
data class ContactPhone(val label: String = "Mobile", val number: String)

@Serializable
data class PersoraContact(
    val id: String,
    val name: String,
    val phoneNumbers: List<ContactPhone> = emptyList(),
    val email: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val address: String = "",
    val birthday: String = "",
    val notes: String = "",
    val category: String = "Other",
    val photoKey: String? = null,
    val favorite: Boolean = false,
    val folderId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    val initials: String get() = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}

/* ---------- Business cards ---------- */

val BUSINESS_SOCIAL_PLATFORMS = listOf("Facebook", "Instagram", "LinkedIn", "X", "YouTube", "TikTok", "WhatsApp", "Telegram", "GitHub", "Pinterest")
val BUSINESS_CARD_STYLES = listOf("garden", "minimal", "midnight", "terracotta")

@Serializable data class BusinessSocialLink(val platform: String, val url: String)
@Serializable data class BusinessCustomLink(val label: String, val url: String)

@Serializable
data class DigitalBusinessCard(
    val id: String,
    val cardId: String? = null,
    val isPublic: Boolean = false,
    val style: String = "garden",
    val fullName: String = "",
    val profilePhotoKey: String? = null,
    val businessLogoKey: String? = null,
    val jobTitle: String = "",
    val company: String = "",
    val phoneNumbers: List<ContactPhone> = emptyList(),
    val email: String = "",
    val websites: List<String> = emptyList(),
    val socialLinks: List<BusinessSocialLink> = emptyList(),
    val address: String = "",
    val bio: String = "",
    val customLinks: List<BusinessCustomLink> = emptyList(),
    val folderId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/* ---------- Medical records ---------- */

val MEDICAL_RECORD_TYPES = listOf("Prescription", "Medical Report", "Lab Test", "Imaging / Scan", "Doctor Visit", "Hospital Record", "Vaccination", "Medical Certificate", "Discharge Summary", "Other")

@Serializable data class MedicalRecordFile(val key: String? = null, val name: String, val size: Long = 0, val type: String = "application/octet-stream")
@Serializable data class MedicalRecordLink(val recordType: String, val recordId: String, val linkKind: String = "related")

@Serializable
data class MedicalRecord(
    val id: String,
    val title: String,
    val recordType: String = "Other",
    val recordDate: String = "",
    val provider: String = "",
    val hospital: String = "",
    val specialty: String = "",
    val notes: String = "",
    val additionalData: String = "",
    val diagnosis: String = "",
    val testName: String = "",
    val testResult: String = "",
    val medicationNotes: String = "",
    val followUpDate: String = "",
    val relatedReminderId: String? = null,
    val file: MedicalRecordFile? = null,
    val links: List<MedicalRecordLink> = emptyList(),
    val folderId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/* ---------- Life timeline ---------- */

@Serializable data class TimelineAttachment(val name: String, val size: Long = 0, val type: String = "application/octet-stream", val key: String? = null)

@Serializable
data class TimelineEvent(
    val id: String,
    val eventType: String = "manual",     // "automatic" | "manual"
    val eventDate: String,
    val title: String,
    val description: String = "",
    val url: String? = null,
    val eventKey: String? = null,
    val recordId: String? = null,
    val linkedRecordIds: List<String> = emptyList(),
    val attachment: TimelineAttachment? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/* ---------- Sharing ---------- */

@Serializable data class ShareUser(val id: String = "", val userId: String = "", val email: String = "", val fullName: String = "")

@Serializable
data class SharedVaultEntry(
    val shareId: String,
    val permission: String,          // view | comment | edit
    val createdAt: String,
    val item: VaultItem,
    val owner: ShareUser,
    val recipient: ShareUser,
    val direction: String,           // incoming | outgoing
)

@Serializable
data class RecordShareEntry(
    val shareId: String,
    val resourceType: String,        // contact | business_card
    val resourceId: String,
    val contact: PersoraContact? = null,
    val card: DigitalBusinessCard? = null,
    val owner: ShareUser,
    val recipient: ShareUser,
    val direction: String,
    val createdAt: String,
)

@Serializable
data class ShareNotification(
    val id: String,
    val kind: String,                // shared | permission_changed | unshared | reminder | alarm
    val itemTitle: String,
    val actorName: String,
    val message: String,
    val createdAt: String,
    val readAt: String? = null,
)

@Serializable data class ShareComment(val id: String, val shareId: String, val authorId: String, val authorName: String, val body: String, val createdAt: String)

/* ---------- Billing ---------- */

@Serializable
data class SubscriptionPlan(
    val id: String = "", val slug: String = "", val name: String = "", val description: String = "",
    val storage_gb: Double = 0.0, val price_per_gb_monthly: Double = 0.0, val active: Boolean = true,
    val sort_order: Int = 0, val monthly_price: Double = 0.0, val currency: String = "BDT",
)

@Serializable
data class PaymentMethod(val id: String = "", val name: String = "", val accountName: String = "", val accountIdentifier: String = "", val instructions: String = "", val active: Boolean = true, val sort_order: Int = 0)

@Serializable
data class BillingSettings(val currency: String = "BDT", val manualInstructions: String = "", val billingEnabled: Boolean = false, val minTermMonths: Int = 1, val maxTermMonths: Int = 12)

@Serializable
data class PaymentRecord(
    val id: String = "", val plan_name: String = "", val storage_gb: Double = 0.0, val amount: Double = 0.0, val currency: String = "BDT",
    val method: String = "", val reference: String = "", val status: String = "pending", val billing_period: String? = null,
    val duration_count: Int? = null, val term_months: Int? = null, val submitted_at: String = "", val reviewed_at: String? = null, val admin_note: String? = null,
)

@Serializable
data class StorageUsage(
    val bytesUsed: Long = 0, val fileBytes: Long = 0, val databaseBytes: Long = 0, val databaseRecordCount: Long = 0, val objectCount: Long = 0,
    val storageLimitBytes: Long = 0, val storageLimitGb: Double = 0.0, val planName: String = "",
)

@Serializable
data class CurrentSubscription(val plan_id: String = "", val plan_name: String = "", val storage_limit_gb: Double = 0.0, val status: String = "", val current_period_end: String? = null)

@Serializable
data class BillingSnapshot(
    val subscription: CurrentSubscription = CurrentSubscription(),
    val plans: List<SubscriptionPlan> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val storage: StorageUsage = StorageUsage(),
    val billingSettings: BillingSettings = BillingSettings(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val maxUploadMb: Int = 25,
    val uploadsEnabled: Boolean = false,
)

/* ---------- Smart scan ---------- */

@Serializable data class SmartScanFieldDefinition(val key: String, val label: String, val kind: String? = null, val options: List<String>? = null)
@Serializable data class SmartScanFieldResult(val value: String = "", val confidence: String = "low", val evidence: String? = null, val reason: String? = null)
@Serializable
data class SmartScanResult(
    val documentType: String = "",
    val documentTypeConfidence: String = "low",
    val fields: Map<String, SmartScanFieldResult> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val cached: Boolean = false,
    val pagesProcessed: Int? = null,
    val cacheWarning: String? = null,
)

/* ---------- Misc ---------- */

@Serializable data class AlarmRingtone(val id: String, val name: String, val type: String = "", val size: Long = 0)
@Serializable data class DocumentTypeOption(val id: String = "", val name: String, val active: Boolean = true, val sort_order: Int = 0)
@Serializable
data class SiteContent(
    val privacyTitle: String = "Privacy Policy", val privacyBody: String = "",
    val termsTitle: String = "Terms of Service", val termsBody: String = "",
    val contactTitle: String = "Contact", val contactBody: String = "", val contactEmail: String = "",
    val contactPhone: String = "", val contactWhatsApp: String = "", val contactAddress: String = "",
)

data class TransferProgress(val loaded: Long, val total: Long) {
    val percent: Int get() = if (total <= 0) 0 else ((loaded * 100) / total).toInt().coerceIn(0, 100)
}

/** Built-in ringtones available on the web (src/lib/ringtone.ts). IDs are stored in item metadata. */
data class BuiltinRingtone(val id: String, val name: String)
val BUILTIN_RINGTONES = listOf(
    BuiltinRingtone("builtin-soft", "Persora soft chime"),
    BuiltinRingtone("builtin-pulse", "Persora pulse"),
)
