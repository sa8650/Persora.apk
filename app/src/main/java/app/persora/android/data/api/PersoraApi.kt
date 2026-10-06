package app.persora.android.data.api

import app.persora.android.core.network.ApiClient
import app.persora.android.core.network.ApiException
import app.persora.android.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.InputStream
import java.net.URLEncoder

/**
 * Every member-facing route of cloudflare/api.js + cloudflare/timeline.js + cloudflare/smart-scan.js.
 * Row parsers mirror fromRow / contactFromRow / businessCardFromRow / medicalRecordFromRow in cloud.ts.
 */
class PersoraApi(private val client: ApiClient) {

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    /* ===================== Auth (src/lib/auth.ts) ===================== */

    suspend fun login(identifier: String, password: String): AppUser =
        parseUser(client.post("/auth/login", buildJsonObject { put("identifier", identifier.trim()); put("password", password) }).obj()["user"].obj())

    suspend fun register(fullName: String, email: String, password: String): AppUser =
        parseUser(client.post("/auth/register", buildJsonObject { put("fullName", fullName.trim()); put("email", email.trim()); put("password", password) }).obj()["user"].obj())

    /** Returns null on 401 (no/expired session) like getCurrentUser(). */
    suspend fun me(): AppUser? = try { parseUser(client.get("/auth/me").obj()["user"].obj()) } catch (e: ApiException) { if (e.isUnauthorized) null else throw e }
    suspend fun logout() { runCatching { client.post("/auth/logout") } }
    suspend fun changePassword(current: String, new: String) { client.post("/auth/password", buildJsonObject { put("currentPassword", current); put("newPassword", new) }) }
    suspend fun updateProfile(fullName: String, timezone: String, avatarUrl: String?) {
        client.patch("/profile", buildJsonObject { put("fullName", fullName); put("timezone", timezone); if (avatarUrl != null) put("avatarUrl", avatarUrl) })
    }
    suspend fun deleteAccount() { client.delete("/account") }
    suspend fun health(): Boolean = runCatching { client.get("/health").obj()["ok"].bool() }.getOrDefault(false)

    private fun parseUser(o: JsonObject?): AppUser = AppUser(
        id = o["id"].str(), userId = o["userId"].str(), email = o["email"].str(), fullName = o["fullName"].str("Persora member"),
        role = o["role"].str("user"), timezone = o["timezone"].str("Asia/Dhaka"), avatarUrl = o["avatarUrl"].str(),
    )

    /* ===================== Vault items / folders ===================== */

    fun vaultItemFromRow(o: JsonObject?): VaultItem {
        val fileName = o["file_name"].strOrNull()
        return VaultItem(
            id = o["id"].str(), section = o["section"].str("documents"), title = o["title"].str("Untitled"), subtitle = o["subtitle"].strOrNull(),
            metadata = o.stringMap("metadata"), createdAt = o["created_at"].str(), updatedAt = o["updated_at"].str(),
            file = fileName?.let { VaultFile(key = o["file_key"].strOrNull(), name = it, size = o["file_size"].strOrNull()?.toLongOrNull() ?: o["file_size"].long().takeIf { s -> s > 0 }, type = o["file_type"].strOrNull()) },
            favorite = o["favorite"].bool(), pinned = o["pinned"].bool(), folderId = o["folder_id"].strOrNull(),
        )
    }

    suspend fun loadVaultItems(): List<VaultItem> = client.get("/vault/items").arr().map { vaultItemFromRow(it.obj()) }

    /** Server requires a client-generated UUID in `id` for both create and update (saveOwnedVaultItem). */
    suspend fun saveVaultItem(item: VaultItem): VaultItem = vaultItemFromRow(client.post("/vault/items", buildJsonObject {
        put("id", item.id); put("section", item.section); put("title", item.title); item.subtitle?.let { put("subtitle", it) }
        putJsonObject("metadata") { item.metadata.forEach { (k, v) -> put(k, v) } }
        put("favorite", item.favorite); put("pinned", item.pinned)
        if (item.folderId != null) put("folderId", item.folderId) else put("folderId", JsonNull)
        if (item.file?.key != null) putJsonObject("file") { put("key", item.file.key); put("name", item.file.name); item.file.size?.let { put("size", it) }; item.file.type?.let { put("type", it) } }
        else put("file", JsonNull)
    }).obj())

    suspend fun deleteVaultItem(id: String) { client.delete("/vault/items?id=${enc(id)}") }

    private fun folderFromRow(o: JsonObject?) = VaultFolder(id = o["id"].str(), scope = o["scope"].str("documents"), name = o["name"].str("Untitled folder"), color = o["color"].str("blue"), pinned = o["pinned"].bool(), createdAt = o["created_at"].str(), updatedAt = o["updated_at"].str())
    suspend fun loadFolders(scope: String): List<VaultFolder> = client.get("/vault/folders?scope=${enc(scope)}").arr().map { folderFromRow(it.obj()) }
    suspend fun saveFolder(id: String?, scope: String, name: String, color: String, pinned: Boolean): VaultFolder =
        folderFromRow(client.post("/vault/folders", buildJsonObject { if (id != null) put("id", id); put("scope", scope); put("name", name); put("color", color); put("pinned", pinned) }).obj())
    suspend fun deleteFolder(id: String, scope: String) { client.delete("/vault/folders?id=${enc(id)}&scope=${enc(scope)}") }

    /* ===================== Files (private R2 through the API) ===================== */

    suspend fun uploadVaultFile(name: String, mime: String, size: Long, open: () -> InputStream, onProgress: ((Long, Long) -> Unit)? = null): VaultFile {
        val o = client.upload("/upload", name, mime, size, open, onProgress = onProgress).obj()
        return VaultFile(key = o["key"].str(), name = o["name"].str(name), size = o["size"].long(size), type = o["type"].str(mime))
    }
    suspend fun deleteVaultFile(key: String) { client.delete("/file?key=${enc(key)}") }

    /** Streams a private file; the caller must close the response. Name is decoded from X-File-Name like openVaultFile(). */
    suspend fun openVaultFile(key: String): FileDownload {
        val response = client.getRaw("/file?key=${enc(key)}")
        return FileDownload(response.header("X-File-Name")?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: key.substringAfterLast('/'), response.header("Content-Type") ?: "application/octet-stream", response.body!!.byteStream(), response)
    }

    /* ===================== Contacts (CONTACTS.md) ===================== */

    private fun phones(el: JsonElement?): List<ContactPhone> = el.arr().mapNotNull { it.obj() }.map { ContactPhone(it["label"].str("Mobile"), it["number"].str()) }.filter { it.number.isNotBlank() }

    fun contactFromRow(o: JsonObject?) = PersoraContact(
        id = o["id"].str(), name = o["full_name"].str(), phoneNumbers = phones(o["phone_numbers"]), email = o["email"].str(), company = o["company"].str(),
        jobTitle = o["job_title"].str(), address = o["address"].str(), birthday = o["birthday"].str(), notes = o["notes"].str(), category = o["category"].str("Other"),
        photoKey = o["photo_key"].strOrNull(), favorite = o["favorite"].bool(), folderId = o["folder_id"].strOrNull(), createdAt = o["created_at"].str(), updatedAt = o["updated_at"].str(),
    )

    suspend fun loadContacts(): List<PersoraContact> = client.get("/contacts").arr().map { contactFromRow(it.obj()) }

    suspend fun saveContact(c: PersoraContact, isNew: Boolean, expectedUpdatedAt: String? = null): PersoraContact = contactFromRow(client.post("/contacts", buildJsonObject {
        if (!isNew) put("id", c.id)
        put("name", c.name); putJsonArray("phoneNumbers") { c.phoneNumbers.forEach { p -> addJsonObject { put("label", p.label); put("number", p.number) } } }
        put("email", c.email); put("company", c.company); put("jobTitle", c.jobTitle); put("address", c.address); put("birthday", c.birthday); put("notes", c.notes)
        put("category", c.category); put("favorite", c.favorite)
        if (c.photoKey != null) put("photoKey", c.photoKey) else put("photoKey", JsonNull)
        if (c.folderId != null) put("folderId", c.folderId) else put("folderId", JsonNull)
        if (!isNew && expectedUpdatedAt != null) put("expectedUpdatedAt", expectedUpdatedAt)
    }).obj())

    suspend fun deleteContact(id: String) { client.delete("/contacts?id=${enc(id)}") }
    suspend fun mergeContacts(primaryId: String, duplicateIds: List<String>): PersoraContact =
        contactFromRow(client.post("/contacts/merge", buildJsonObject { put("primaryId", primaryId); putJsonArray("duplicateIds") { duplicateIds.forEach { add(it) } } }).obj())
    fun contactPhotoUrl(contactId: String) = client.url("/contacts/photo?id=${enc(contactId)}")

    /* ===================== Business cards (BUSINESS_CARDS.md) ===================== */

    fun businessCardFromRow(o: JsonObject?) = DigitalBusinessCard(
        id = o["id"].str(), cardId = o["card_id"].strOrNull(), isPublic = o["is_public"].bool(), style = o["card_style"].str("garden"), fullName = o["full_name"].str(),
        profilePhotoKey = o["profile_photo_key"].strOrNull(), businessLogoKey = o["business_logo_key"].strOrNull(), jobTitle = o["job_title"].str(), company = o["company"].str(),
        phoneNumbers = phones(o["phone_numbers"]), email = o["email"].str(), websites = o["websites"].arr().map { it.str() }.filter { it.isNotBlank() },
        socialLinks = o["social_links"].arr().mapNotNull { it.obj() }.map { BusinessSocialLink(it["platform"].str("Facebook"), it["url"].str()) }.filter { it.url.isNotBlank() },
        address = o["address"].str(), bio = o["bio"].str(),
        customLinks = o["custom_links"].arr().mapNotNull { it.obj() }.map { BusinessCustomLink(it["label"].str("Link"), it["url"].str()) }.filter { it.url.isNotBlank() },
        folderId = o["folder_id"].strOrNull(), createdAt = o["created_at"].str(), updatedAt = o["updated_at"].str(),
    )

    suspend fun loadBusinessCards(): List<DigitalBusinessCard> = client.get("/business-cards").arr().map { businessCardFromRow(it.obj()) }

    suspend fun saveBusinessCard(card: DigitalBusinessCard, isNew: Boolean): DigitalBusinessCard = businessCardFromRow(client.post("/business-cards", buildJsonObject {
        if (!isNew) put("id", card.id)
        if (card.cardId != null) put("cardId", card.cardId) else put("cardId", JsonNull)
        put("isPublic", card.isPublic); put("style", card.style); put("fullName", card.fullName); put("jobTitle", card.jobTitle); put("company", card.company)
        putJsonArray("phoneNumbers") { card.phoneNumbers.forEach { p -> addJsonObject { put("label", p.label); put("number", p.number) } } }
        put("email", card.email); putJsonArray("websites") { card.websites.forEach { add(it) } }
        putJsonArray("socialLinks") { card.socialLinks.forEach { s -> addJsonObject { put("platform", s.platform); put("url", s.url) } } }
        put("address", card.address); put("bio", card.bio)
        putJsonArray("customLinks") { card.customLinks.forEach { l -> addJsonObject { put("label", l.label); put("url", l.url) } } }
        if (card.profilePhotoKey != null) put("profilePhotoKey", card.profilePhotoKey) else put("profilePhotoKey", JsonNull)
        if (card.businessLogoKey != null) put("businessLogoKey", card.businessLogoKey) else put("businessLogoKey", JsonNull)
        if (card.folderId != null) put("folderId", card.folderId) else put("folderId", JsonNull)
    }).obj())

    suspend fun deleteBusinessCard(id: String) { client.delete("/business-cards?id=${enc(id)}") }
    fun publicCardUrl(cardId: String) = "${client.baseUrl.removeSuffix("/api")}/card/${enc(cardId)}"
    suspend fun fetchPublicBusinessCard(cardId: String): DigitalBusinessCard = businessCardFromRow(client.get("/public-cards/${enc(cardId)}").obj())
    suspend fun reportPublicBusinessCard(cardId: String, reason: String, details: String) { client.post("/public-cards/${enc(cardId)}/report", buildJsonObject { put("reason", reason); put("details", details) }) }
    fun vaultFileUrl(key: String) = client.url("/file?key=${enc(key)}")
    fun publicCardPhotoUrl(cardId: String, kind: String) = client.url("/public-cards/${enc(cardId)}/photo?kind=$kind")

    /* ===================== Medical records ===================== */

    fun medicalRecordFromRow(o: JsonObject?): MedicalRecord {
        val fileName = o["file_name"].strOrNull()
        val links = o["links"].arr().mapNotNull { it.obj() }.mapNotNull { l ->
            val type = l["record_type"].str(); val id = l["record_id"].strOrNull()
            if ((type == "contact" || type == "vault_item") && id != null) MedicalRecordLink(type, id, if (l["link_kind"].str() == "reminder") "reminder" else "related") else null
        }
        return MedicalRecord(
            id = o["id"].str(), title = o["title"].str(), recordType = o["record_type"].str("Other"), recordDate = o["record_date"].str(), provider = o["provider"].str(),
            hospital = o["hospital"].str(), specialty = o["specialty"].str(), notes = o["notes"].str(), diagnosis = o["diagnosis"].str(), testName = o["test_name"].str(),
            testResult = o["test_result"].str(), medicationNotes = o["medication_notes"].str(), followUpDate = o["follow_up_date"].str(), relatedReminderId = o["related_reminder_id"].strOrNull(),
            file = fileName?.let { MedicalRecordFile(o["file_key"].strOrNull(), it, o["file_size"].long(), o["file_type"].str("application/octet-stream")) },
            links = links.filter { it.linkKind != "reminder" }, folderId = o["folder_id"].strOrNull(), createdAt = o["created_at"].str(), updatedAt = o["updated_at"].str(),
        )
    }

    suspend fun loadMedicalRecords(): List<MedicalRecord> = client.get("/medical-records").arr().map { medicalRecordFromRow(it.obj()) }

    suspend fun saveMedicalRecord(r: MedicalRecord, isNew: Boolean, removeFile: Boolean = false): MedicalRecord = medicalRecordFromRow(client.post("/medical-records", buildJsonObject {
        if (!isNew) put("id", r.id)
        put("title", r.title); put("recordType", r.recordType); put("recordDate", r.recordDate); put("provider", r.provider); put("hospital", r.hospital); put("specialty", r.specialty)
        put("notes", r.notes); put("diagnosis", r.diagnosis); put("testName", r.testName); put("testResult", r.testResult); put("medicationNotes", r.medicationNotes); put("followUpDate", r.followUpDate)
        if (r.relatedReminderId != null) put("relatedReminderId", r.relatedReminderId) else put("relatedReminderId", JsonNull)
        putJsonArray("links") { r.links.forEach { l -> addJsonObject { put("recordType", l.recordType); put("recordId", l.recordId); put("linkKind", l.linkKind) } } }
        if (removeFile || r.file?.key == null) put("file", JsonNull) else putJsonObject("file") { put("key", r.file.key); put("name", r.file.name); put("size", r.file.size); put("type", r.file.type) }
        if (r.folderId != null) put("folderId", r.folderId) else put("folderId", JsonNull)
    }).obj())

    suspend fun deleteMedicalRecord(id: String) { client.delete("/medical-records?id=${enc(id)}") }
    suspend fun uploadMedicalFile(name: String, mime: String, size: Long, open: () -> InputStream, onProgress: ((Long, Long) -> Unit)? = null): MedicalRecordFile {
        val o = client.upload("/medical-records/upload", name, mime, size, open, onProgress = onProgress).obj()
        return MedicalRecordFile(o["key"].str(), o["name"].str(name), o["size"].long(size), o["type"].str(mime))
    }
    suspend fun deleteMedicalFileByKey(key: String) { client.delete("/medical-records/upload?key=${enc(key)}") }
    suspend fun openMedicalFile(recordId: String): FileDownload {
        val response = client.getRaw("/medical-records/file?id=${enc(recordId)}")
        return FileDownload(response.header("X-File-Name")?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: "medical-record", response.header("Content-Type") ?: "application/octet-stream", response.body!!.byteStream(), response)
    }

    /* ===================== Life timeline (cloudflare/timeline.js) ===================== */

    private fun timelineFromPayload(o: JsonObject?): TimelineEvent {
        val a = o["attachment"].obj()
        return TimelineEvent(
            id = o["id"].str(), eventType = if (o["eventType"].str() == "automatic") "automatic" else "manual", eventDate = o["eventDate"].str(), title = o["title"].str(),
            description = o["description"].str(), url = o["url"].strOrNull(), eventKey = o["eventKey"].strOrNull(), recordId = o["recordId"].strOrNull(),
            linkedRecordIds = o["linkedRecordIds"].arr().map { it.str() }.filter { it.isNotBlank() },
            attachment = a?.let { if (it["name"].strOrNull() != null) TimelineAttachment(it["name"].str(), it["size"].long(), it["type"].str("application/octet-stream"), it["key"].strOrNull()) else null },
            createdAt = o["createdAt"].str(), updatedAt = o["updatedAt"].str(),
        )
    }

    suspend fun loadTimeline(): List<TimelineEvent> = client.get("/timeline").arr().map { timelineFromPayload(it.obj()) }
    suspend fun saveTimelineEvent(e: TimelineEvent, isNew: Boolean, removeAttachment: Boolean = false): TimelineEvent = timelineFromPayload(client.post("/timeline", buildJsonObject {
        if (!isNew) put("id", e.id)
        put("eventDate", e.eventDate); put("title", e.title); put("description", e.description); put("url", e.url ?: "")
        putJsonArray("linkedRecordIds") { e.linkedRecordIds.forEach { add(it) } }
        if (removeAttachment || e.attachment == null) put("attachment", JsonNull) else putJsonObject("attachment") { put("name", e.attachment.name); put("size", e.attachment.size); put("type", e.attachment.type); e.attachment.key?.let { put("key", it) } }
    }).obj())
    suspend fun deleteTimelineEvent(id: String) { client.delete("/timeline?id=${enc(id)}") }
    suspend fun uploadTimelineAttachment(name: String, mime: String, size: Long, open: () -> InputStream): TimelineAttachment {
        val o = client.upload("/timeline/attachment", name, mime, size, open).obj()
        return TimelineAttachment(o["name"].str(name), o["size"].long(size), o["type"].str(mime), o["key"].strOrNull())
    }
    suspend fun openTimelineAttachment(eventId: String): FileDownload {
        val response = client.getRaw("/timeline/attachment?eventId=${enc(eventId)}")
        return FileDownload(response.header("X-File-Name")?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: "timeline-attachment", response.header("Content-Type") ?: "application/octet-stream", response.body!!.byteStream(), response)
    }

    /* ===================== Sharing ===================== */

    private fun shareUser(o: JsonObject?) = ShareUser(o["id"].str(), o["userId"].str(), o["email"].str(), o["fullName"].str())

    suspend fun loadSharedItems(direction: String): List<SharedVaultEntry> = client.get("/shares?direction=$direction").arr().mapNotNull { it.obj() }.map { o ->
        SharedVaultEntry(shareId = o["shareId"].strOrNull() ?: o["id"].str(), permission = o["permission"].str("view"), createdAt = o["createdAt"].str(),
            item = vaultItemFromRow(o["item"].obj()), owner = shareUser(o["owner"].obj()), recipient = shareUser(o["recipient"].obj()), direction = direction)
    }
    suspend fun loadRecordShares(direction: String): List<RecordShareEntry> = client.get("/record-shares?direction=$direction").arr().mapNotNull { it.obj() }.mapNotNull { o ->
        val type = o["resourceType"].str(); val raw = o["record"].obj() ?: return@mapNotNull null
        if (type != "contact" && type != "business_card") return@mapNotNull null
        RecordShareEntry(shareId = o["shareId"].str(), resourceType = type, resourceId = o["resourceId"].strOrNull() ?: raw["id"].str(),
            contact = if (type == "contact") contactFromRow(raw) else null, card = if (type == "business_card") businessCardFromRow(raw) else null,
            owner = shareUser(o["owner"].obj()), recipient = shareUser(o["recipient"].obj()), direction = direction, createdAt = o["createdAt"].str())
    }
    suspend fun createDocumentShare(itemId: String, recipient: String, permission: String) { client.post("/shares", buildJsonObject { put("itemId", itemId); put("recipient", recipient); put("permission", permission) }) }
    suspend fun changeSharePermission(shareId: String, permission: String) { client.patch("/shares", buildJsonObject { put("shareId", shareId); put("permission", permission) }) }
    suspend fun revokeDocumentShare(shareId: String) { client.delete("/shares", buildJsonObject { put("shareId", shareId) }) }
    suspend fun createRecordShare(resourceType: String, resourceId: String, recipient: String) { client.post("/record-shares", buildJsonObject { put("resourceType", resourceType); put("resourceId", resourceId); put("recipient", recipient) }) }
    suspend fun revokeRecordShare(shareId: String) { client.delete("/record-shares", buildJsonObject { put("shareId", shareId) }) }
    suspend fun saveSharedDocument(shareId: String, item: VaultItem): VaultItem = vaultItemFromRow(client.post("/shares/item", buildJsonObject {
        put("shareId", shareId); put("id", item.id); put("section", item.section); put("title", item.title); item.subtitle?.let { put("subtitle", it) }
        putJsonObject("metadata") { item.metadata.forEach { (k, v) -> put(k, v) } }; put("favorite", item.favorite); put("pinned", item.pinned)
        if (item.file?.key != null) putJsonObject("file") { put("key", item.file.key); put("name", item.file.name); item.file.size?.let { put("size", it) }; item.file.type?.let { put("type", it) } }
    }).obj())
    suspend fun uploadSharedFile(shareId: String, name: String, mime: String, size: Long, open: () -> InputStream): VaultFile {
        val o = client.upload("/shares/upload?shareId=${enc(shareId)}", name, mime, size, open).obj()
        return VaultFile(o["key"].str(), o["name"].str(name), o["size"].long(size), o["type"].str(mime))
    }
    suspend fun loadShareComments(shareId: String): List<ShareComment> = client.get("/shares/comments?shareId=${enc(shareId)}").arr().mapNotNull { it.obj() }.map { commentFrom(it) }
    suspend fun addShareComment(shareId: String, body: String): ShareComment = commentFrom(client.post("/shares/comments", buildJsonObject { put("shareId", shareId); put("body", body) }).obj())
    private fun commentFrom(o: JsonObject?) = ShareComment(o["id"].str(), o["shareId"].str(), o["authorId"].str(), o["authorName"].str("Persora member"), o["body"].str(), o["createdAt"].str())

    suspend fun loadNotifications(): List<ShareNotification> = client.get("/notifications").arr().mapNotNull { it.obj() }.map { o ->
        ShareNotification(o["id"].str(), o["kind"].str("shared"), o["item_title"].str("Shared document"), o["actor_name"].str("Persora user"), o["message"].str(), o["created_at"].str(), o["read_at"].strOrNull())
    }
    suspend fun markNotificationsRead() { client.patch("/notifications") }

    /* ===================== Billing / storage / public config ===================== */

    suspend fun loadBilling(): BillingSnapshot = client.json.decodeFromJsonElement(BillingSnapshot.serializer(), client.get("/billing"))
    suspend fun loadStorageUsage(): StorageUsage = client.json.decodeFromJsonElement(StorageUsage.serializer(), client.get("/storage/usage"))
    suspend fun submitPayment(planId: String, methodId: String, reference: String, billingPeriod: String, durationCount: Int): PaymentRecord =
        client.json.decodeFromJsonElement(PaymentRecord.serializer(), client.post("/payments", buildJsonObject { put("planId", planId); put("methodId", methodId); put("reference", reference); put("billingPeriod", billingPeriod); put("durationCount", durationCount) }))
    suspend fun loadDocumentTypes(): List<DocumentTypeOption> = client.get("/document-types").arr().mapNotNull { it.obj() }.map { DocumentTypeOption(it["id"].str(), it["name"].str(), it["active"].bool(true), it["sort_order"].int()) }
    suspend fun loadSiteContent(): SiteContent = client.json.decodeFromJsonElement(SiteContent.serializer(), client.get("/site-content"))
    suspend fun loadAlarmRingtones(): List<AlarmRingtone> = client.get("/alarm-ringtones").arr().mapNotNull { it.obj() }.map { AlarmRingtone(it["id"].str(), it["name"].str(), it["type"].str(), it["size"].long()) }
    fun alarmRingtoneAudioUrl(id: String) = client.url("/alarm-ringtones/${enc(id)}/audio")
    suspend fun exportAccount(): FileDownload {
        val response = client.getRaw("/account/export")
        return FileDownload("persora-export.tar", response.header("Content-Type") ?: "application/x-tar", response.body!!.byteStream(), response)
    }

    /* ===================== Smart scan (cloudflare/smart-scan.js) ===================== */

    suspend fun smartScan(name: String, mime: String, size: Long, open: () -> InputStream, section: String, fields: List<SmartScanFieldDefinition>, retry: Boolean = false): SmartScanResult {
        val fieldsJson = client.json.encodeToString(fields)
        val el = client.upload("/smart-scan", name, mime, size, open, extraFields = mapOf("section" to section, "fields" to fieldsJson, "retry" to retry.toString()))
        return client.json.decodeFromJsonElement(SmartScanResult.serializer(), el)
    }
}

/** A streamed private file. Always `close()` when done. */
class FileDownload(val name: String, val contentType: String, val stream: InputStream, private val response: okhttp3.Response) : AutoCloseable {
    override fun close() { runCatching { response.close() } }
}

internal fun JsonPrimitive?.orNull(): JsonElement = this ?: JsonNull
