package app.persora.android.data.repository

import app.persora.android.core.network.ApiException
import app.persora.android.core.storage.JsonCache
import app.persora.android.data.api.PersoraApi
import app.persora.android.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for the signed-in member's records. Cache-first: the last server copy is shown
 * instantly, then `refreshAll()` reconciles with the API (the web client polls every 30 s while signed in).
 */
class VaultRepository(
    val api: PersoraApi,
    private val cache: JsonCache,
    private val session: SessionManager,
    private val onScheduleChanged: (List<VaultItem>) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val items = MutableStateFlow<List<VaultItem>>(emptyList())
    val contacts = MutableStateFlow<List<PersoraContact>>(emptyList())
    val businessCards = MutableStateFlow<List<DigitalBusinessCard>>(emptyList())
    val medicalRecords = MutableStateFlow<List<MedicalRecord>>(emptyList())
    val timeline = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val notifications = MutableStateFlow<List<ShareNotification>>(emptyList())
    val incomingShares = MutableStateFlow<List<SharedVaultEntry>>(emptyList())
    val outgoingShares = MutableStateFlow<List<SharedVaultEntry>>(emptyList())
    val incomingRecordShares = MutableStateFlow<List<RecordShareEntry>>(emptyList())
    val outgoingRecordShares = MutableStateFlow<List<RecordShareEntry>>(emptyList())
    val folders = MutableStateFlow<Map<String, List<VaultFolder>>>(emptyMap())
    val ringtones = MutableStateFlow<List<AlarmRingtone>>(emptyList())
    val storage = MutableStateFlow<StorageUsage?>(null)

    private val _refreshing = MutableStateFlow(false)
    /** True while the last read failed because the device has no connection — screens keep showing the cache. */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()
    /** Epoch millis of the last successful full sync (shown in Settings / pull-to-refresh). */
    val lastSyncedAt = MutableStateFlow(0L)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    val unreadCount: Int get() = notifications.value.count { it.readAt == null }

    /* ---------- cache plumbing ---------- */

    private val foldersSerializer = MapSerializer(String.serializer(), ListSerializer(VaultFolder.serializer()))

    private fun <T> load(key: String, serializer: KSerializer<List<T>>, into: MutableStateFlow<List<T>>) {
        cache.read(key)?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull()?.let { into.value = it } }
    }
    private fun <T> store(key: String, serializer: KSerializer<List<T>>, value: List<T>) { cache.write(key, json.encodeToString(serializer, value)) }
    /** Publish + persist only when the server copy differs from what's on screen, so background syncs never cause visible reloads. */
    private fun <T> publish(flow: MutableStateFlow<List<T>>, key: String, serializer: KSerializer<List<T>>, fresh: List<T>): Boolean {
        if (flow.value == fresh) return false
        flow.value = fresh; store(key, serializer, fresh); return true
    }

    fun loadFromCache() {
        load("vault_items", ListSerializer(VaultItem.serializer()), items)
        load("contacts", ListSerializer(PersoraContact.serializer()), contacts)
        load("business_cards", ListSerializer(DigitalBusinessCard.serializer()), businessCards)
        load("medical_records", ListSerializer(MedicalRecord.serializer()), medicalRecords)
        load("timeline", ListSerializer(TimelineEvent.serializer()), timeline)
        load("notifications", ListSerializer(ShareNotification.serializer()), serverNotifications)
        load("local_notifications", ListSerializer(ShareNotification.serializer()), localNotifications)
        rebuildNotifications()
        load("shares_in", ListSerializer(SharedVaultEntry.serializer()), incomingShares)
        load("shares_out", ListSerializer(SharedVaultEntry.serializer()), outgoingShares)
        load("record_shares_in", ListSerializer(RecordShareEntry.serializer()), incomingRecordShares)
        load("record_shares_out", ListSerializer(RecordShareEntry.serializer()), outgoingRecordShares)
        load("ringtones", ListSerializer(AlarmRingtone.serializer()), ringtones)
        cache.read("storage")?.let { raw -> runCatching { json.decodeFromString(StorageUsage.serializer(), raw) }.getOrNull()?.let { storage.value = it } }
        cache.read("folders")?.let { raw -> runCatching { json.decodeFromString(foldersSerializer, raw) }.getOrNull()?.let { folders.value = it } }
        lastSyncedAt.value = cache.read("last_synced")?.toLongOrNull() ?: 0L
        // Alarms and reminders must ring even if the phone never comes back online: schedule straight from the cache.
        if (items.value.isNotEmpty()) onScheduleChanged(items.value)
        onScheduleChanged(items.value)
    }

    fun clear() {
        items.value = emptyList(); contacts.value = emptyList(); businessCards.value = emptyList(); medicalRecords.value = emptyList(); timeline.value = emptyList()
        notifications.value = emptyList(); serverNotifications.value = emptyList(); localNotifications.value = emptyList(); incomingShares.value = emptyList(); outgoingShares.value = emptyList(); incomingRecordShares.value = emptyList(); outgoingRecordShares.value = emptyList()
        ringtones.value = emptyList(); lastSyncedAt.value = 0L
        folders.value = emptyMap(); storage.value = null
    }

    private suspend fun <T> guard(block: suspend () -> T): T? = try { block().also { _offline.value = false } } catch (e: ApiException) {
        when {
            e.isUnauthorized -> session.sessionExpired()
            e.isOffline -> _offline.value = true // silent: cached data stays on screen, no toast spam while polling
            else -> _lastError.value = e.message
        }
        null
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e // a screen left the composition mid-request: not an error, just stop
    } catch (e: Exception) { _lastError.value = e.message ?: "Something went wrong."; null }

    fun clearError() { _lastError.value = null }

    /* ---------- refresh ---------- */

    suspend fun refreshAll() {
        if (_refreshing.value) return
        _refreshing.value = true
        try {
            kotlinx.coroutines.coroutineScope {
                listOf(
                    async { guard { api.loadVaultItems() }?.let { if (publish(items, "vault_items", ListSerializer(VaultItem.serializer()), it)) onScheduleChanged(it) } },
                    async { guard { api.loadContacts() }?.let { publish(contacts, "contacts", ListSerializer(PersoraContact.serializer()), it) } },
                    async { guard { api.loadBusinessCards() }?.let { publish(businessCards, "business_cards", ListSerializer(DigitalBusinessCard.serializer()), it) } },
                    async { guard { api.loadMedicalRecords() }?.let { publish(medicalRecords, "medical_records", ListSerializer(MedicalRecord.serializer()), it) } },
                    async { guard { api.loadTimeline() }?.let { publish(timeline, "timeline", ListSerializer(TimelineEvent.serializer()), it) } },
                    async { refreshNotifications() },
                    async { refreshShares() },
                    async { guard { api.loadStorageUsage() }?.let { if (storage.value != it) { storage.value = it; cache.write("storage", json.encodeToString(StorageUsage.serializer(), it)) } } },
                    async { guard { api.loadAlarmRingtones() }?.let { publish(ringtones, "ringtones", ListSerializer(AlarmRingtone.serializer()), it) } },
                    async { refreshFolders("documents"); refreshFolders("notes"); refreshFolders("contacts") },
                ).awaitAll()
            }
            if (!_offline.value) { lastSyncedAt.value = System.currentTimeMillis(); cache.write("last_synced", lastSyncedAt.value.toString()) }
        } finally { _refreshing.value = false }
    }

    /** Light background sync used by the 30 s poll: the collections most likely to change on the website. */
    suspend fun refreshLight() { refreshItems(); refreshNotifications(); refreshContacts() }

    suspend fun refreshItems() { guard { api.loadVaultItems() }?.let { if (publish(items, "vault_items", ListSerializer(VaultItem.serializer()), it)) onScheduleChanged(it) } }
    /**
     * Notifications = sharing activity from the server + reminders/alarms that rang on this device (the web app builds
     * the same `schedule:<itemId>:<key>` entries locally in App.tsx). Both halves are cached so the bell works offline.
     */
    private val serverNotifications = MutableStateFlow<List<ShareNotification>>(emptyList())
    private val localNotifications = MutableStateFlow<List<ShareNotification>>(emptyList())
    private fun rebuildNotifications() {
        val merged = (localNotifications.value + serverNotifications.value).distinctBy { it.id }.sortedByDescending { it.createdAt }
        if (notifications.value != merged) notifications.value = merged
    }
    suspend fun refreshNotifications() {
        guard { api.loadNotifications() }?.let { if (publish(serverNotifications, "notifications", ListSerializer(ShareNotification.serializer()), it)) rebuildNotifications() }
    }
    /** Called by ScheduleReceiver the moment a reminder / alarm fires, so it shows under the bell like on the web. */
    fun recordScheduleFired(itemId: String, title: String, kind: String, atMillis: Long) {
        val key = java.time.Instant.ofEpochMilli(atMillis).toString()
        val entry = ShareNotification(
            id = "schedule:$itemId:$key", kind = kind, itemTitle = title, actorName = title,
            message = if (kind == "alarm") "Your alarm is ringing." else "Your reminder is due now.", createdAt = java.time.Instant.now().toString(),
        )
        localNotifications.update { l -> (listOf(entry) + l.filter { it.id != entry.id }).take(30) }
        store("local_notifications", ListSerializer(ShareNotification.serializer()), localNotifications.value)
        rebuildNotifications()
    }
    suspend fun refreshShares() {
        guard { api.loadSharedItems("incoming") }?.let { publish(incomingShares, "shares_in", ListSerializer(SharedVaultEntry.serializer()), it) }
        guard { api.loadSharedItems("outgoing") }?.let { publish(outgoingShares, "shares_out", ListSerializer(SharedVaultEntry.serializer()), it) }
        guard { api.loadRecordShares("incoming") }?.let { publish(incomingRecordShares, "record_shares_in", ListSerializer(RecordShareEntry.serializer()), it) }
        guard { api.loadRecordShares("outgoing") }?.let { publish(outgoingRecordShares, "record_shares_out", ListSerializer(RecordShareEntry.serializer()), it) }
    }
    suspend fun refreshFolders(scope: String) {
        guard { api.loadFolders(scope) }?.let { list ->
            if (folders.value[scope] == list) return@let
            folders.update { it + (scope to list) }
            cache.write("folders", json.encodeToString(foldersSerializer, folders.value))
        }
    }
    suspend fun refreshRingtones() { guard { api.loadAlarmRingtones() }?.let { ringtones.value = it } }
    suspend fun refreshTimeline() { guard { api.loadTimeline() }?.let { publish(timeline, "timeline", ListSerializer(TimelineEvent.serializer()), it) } }
    suspend fun refreshContacts() { guard { api.loadContacts() }?.let { publish(contacts, "contacts", ListSerializer(PersoraContact.serializer()), it) } }
    suspend fun refreshMedical() { guard { api.loadMedicalRecords() }?.let { publish(medicalRecords, "medical_records", ListSerializer(MedicalRecord.serializer()), it) } }
    suspend fun refreshCards() { guard { api.loadBusinessCards() }?.let { publish(businessCards, "business_cards", ListSerializer(DigitalBusinessCard.serializer()), it) } }

    /* ---------- mutations (optimistic where safe) ---------- */

    suspend fun saveItem(item: VaultItem): VaultItem {
        val saved = api.saveVaultItem(item)
        items.update { list -> if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved }
        store("vault_items", ListSerializer(VaultItem.serializer()), items.value); onScheduleChanged(items.value)
        return saved
    }
    suspend fun deleteItem(item: VaultItem) {
        api.deleteVaultItem(item.id)
        item.file?.key?.let { key -> runCatching { api.deleteVaultFile(key) } }
        items.update { list -> list.filterNot { it.id == item.id } }
        store("vault_items", ListSerializer(VaultItem.serializer()), items.value); onScheduleChanged(items.value)
    }
    suspend fun toggleFavorite(item: VaultItem) = saveItem(item.copy(favorite = !item.favorite))
    suspend fun togglePinned(item: VaultItem) = saveItem(item.copy(pinned = !item.pinned))
    suspend fun setTodoCompleted(item: VaultItem, completed: Boolean) = saveItem(item.copy(metadata = item.metadata + ("completed" to completed.toString())))
    suspend fun setScheduleEnabled(item: VaultItem, enabled: Boolean) = saveItem(item.copy(metadata = item.metadata + ("enabled" to enabled.toString()) - "snoozedUntil"))
    suspend fun snooze(item: VaultItem, untilIso: String) = saveItem(item.copy(metadata = item.metadata + ("snoozedUntil" to untilIso)))
    suspend fun moveToFolder(item: VaultItem, folderId: String?) = saveItem(item.copy(folderId = folderId))

    suspend fun saveFolder(id: String?, scope: String, name: String, color: String, pinned: Boolean): VaultFolder { val f = api.saveFolder(id, scope, name, color, pinned); refreshFolders(scope); return f }
    suspend fun deleteFolder(folder: VaultFolder) { api.deleteFolder(folder.id, folder.scope); refreshFolders(folder.scope); refreshItems() }

    suspend fun saveContact(contact: PersoraContact, isNew: Boolean): PersoraContact {
        val expected = if (isNew) null else contacts.value.firstOrNull { it.id == contact.id }?.updatedAt
        val saved = api.saveContact(contact, isNew, expected)
        contacts.update { list -> if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else (list + saved).sortedBy { it.name.lowercase() } }
        store("contacts", ListSerializer(PersoraContact.serializer()), contacts.value); return saved
    }
    suspend fun deleteContact(id: String) { api.deleteContact(id); contacts.update { l -> l.filterNot { it.id == id } }; store("contacts", ListSerializer(PersoraContact.serializer()), contacts.value) }
    suspend fun mergeContacts(primaryId: String, duplicateIds: List<String>) { api.mergeContacts(primaryId, duplicateIds); refreshContacts() }

    suspend fun saveBusinessCard(card: DigitalBusinessCard, isNew: Boolean): DigitalBusinessCard { val saved = api.saveBusinessCard(card, isNew); refreshCards(); return saved }
    suspend fun deleteBusinessCard(id: String) { api.deleteBusinessCard(id); businessCards.update { l -> l.filterNot { it.id == id } } }

    suspend fun saveMedicalRecord(record: MedicalRecord, isNew: Boolean, removeFile: Boolean): MedicalRecord { val saved = api.saveMedicalRecord(record, isNew, removeFile); refreshMedical(); return saved }
    suspend fun deleteMedicalRecord(id: String) { api.deleteMedicalRecord(id); medicalRecords.update { l -> l.filterNot { it.id == id } } }

    suspend fun saveTimelineEvent(event: TimelineEvent, isNew: Boolean, removeAttachment: Boolean): TimelineEvent { val saved = api.saveTimelineEvent(event, isNew, removeAttachment); refreshTimeline(); return saved }
    suspend fun deleteTimelineEvent(id: String) { api.deleteTimelineEvent(id); timeline.update { l -> l.filterNot { it.id == id } } }

    suspend fun markNotificationsRead() {
        runCatching { api.markNotificationsRead() }
        val now = java.time.Instant.now().toString()
        serverNotifications.update { l -> l.map { if (it.readAt == null) it.copy(readAt = now) else it } }
        localNotifications.update { l -> l.map { if (it.readAt == null) it.copy(readAt = now) else it } }
        store("local_notifications", ListSerializer(ShareNotification.serializer()), localNotifications.value)
        rebuildNotifications()
    }

    fun launch(block: suspend () -> Unit) { scope.launch { guard { block() } } }
}
