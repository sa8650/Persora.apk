package app.persora.android.ui.contacts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.persora.android.MainActivity
import app.persora.android.R
import app.persora.android.appContainer
import app.persora.android.core.network.ApiException
import app.persora.android.core.util.ContactImport
import app.persora.android.data.model.PersoraContact
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** WorkManager-backed phone-contact import: encrypted local payload, foreground progress notification, and retry-safe IDs. */
object ContactImportWork {
    const val TAG = "persora-phone-contact-import"
    const val KEY_PROGRESS = "progress"
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_FAILURE = "failure"
    const val OUTPUT_IMPORTED = "imported"
    const val OUTPUT_FAILED = "failed"
    const val OUTPUT_SKIPPED_DUPLICATES = "skipped_duplicates"
    const val OUTPUT_SKIPPED_INVALID = "skipped_invalid"
    const val OUTPUT_SKIPPED_REPEATED = "skipped_repeated"
    const val KEY_PAYLOAD_PATH = "payload_path"
    const val KEY_JOB_ID = "job_id"
    private const val MAX_PAYLOAD_BYTES = 75 * 1024 * 1024
    internal val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun enqueue(context: Context, entries: List<ContactImport.Entry>, userId: String): UUID {
        val selected = entries.filter { it.selected }
        require(selected.isNotEmpty()) { "There are no new phone contacts to import." }
        val jobId = UUID.randomUUID().toString()
        val payload = StoredContactImport(
            userId = userId,
            contacts = selected.map { entry ->
                val contact = entry.draft.contact.copy(id = entry.draft.contact.id.ifBlank { UUID.randomUUID().toString() })
                StoredContactUpload(contact, entry.draft.photo?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }, entry.draft.photoMime)
            },
            skippedDuplicates = entries.count { !it.selected && it.duplicateOf.isNotBlank() },
            skippedInvalid = entries.sumOf { it.invalidPhones.size },
            skippedRepeated = entries.sumOf { it.duplicatePhones.size },
        )
        val plain = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_PAYLOAD_BYTES) { "The phone contact import is too large to queue safely. Try importing a smaller set of contacts." }
        val directory = File(context.noBackupFilesDir, "contact-imports").apply { mkdirs() }
        val payloadFile = File(directory, "$jobId.bin")
        ContactImportPayloadCipher.encrypt(context, payloadFile, plain)
        val request = OneTimeWorkRequestBuilder<ContactImportWorker>()
            .setInputData(workDataOf(KEY_PAYLOAD_PATH to payloadFile.absolutePath, KEY_JOB_ID to jobId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag("$TAG:$jobId")
            .build()
        try {
            WorkManager.getInstance(context).enqueueUniqueWork("$TAG:$jobId", androidx.work.ExistingWorkPolicy.KEEP, request)
        } catch (error: Exception) {
            payloadFile.delete()
            throw error
        }
        return request.id
    }
}

@Serializable
private data class StoredContactImport(
    val userId: String,
    val contacts: List<StoredContactUpload>,
    val skippedDuplicates: Int,
    val skippedInvalid: Int,
    val skippedRepeated: Int,
)

@Serializable
private data class StoredContactUpload(val contact: PersoraContact, val photoBase64: String?, val photoMime: String)

class ContactImportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val context = applicationContext
    private val appContainer = context.appContainer
    private val notificationId: Int by lazy { (inputData.getString(ContactImportWork.KEY_JOB_ID).orEmpty().hashCode() and 0x3fffffff).coerceAtLeast(1) }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(ContactImportWork.KEY_JOB_ID).orEmpty()
        val path = inputData.getString(ContactImportWork.KEY_PAYLOAD_PATH).orEmpty()
        val payloadFile = File(path)
        var terminal = false
        return try {
            val payloadDirectory = File(context.noBackupFilesDir, "contact-imports").canonicalFile
            val canonicalPayload = payloadFile.canonicalFile
            require(canonicalPayload.parentFile == payloadDirectory && canonicalPayload.isFile) { "The queued contact import is no longer available." }
            val payload = ContactImportWork.json.decodeFromString<StoredContactImport>(ContactImportPayloadCipher.decrypt(context, canonicalPayload).toString(Charsets.UTF_8))
            require(payload.contacts.isNotEmpty()) { "There are no new contacts to import." }
            val activeUser = appContainer.api.me() ?: error("Your Persora session expired. Sign in and start the import again.")
            if (activeUser.id != payload.userId) error("This import belongs to a different Persora account. Sign in to that account and start it again.")
            appContainer.session.updateUser(activeUser)

            val total = payload.contacts.size
            setForeground(foregroundInfo(jobId, done = 0, total = total))
            setProgress(progressData(done = 0, total = total))
            var imported = 0
            var failed = 0
            for ((index, entry) in payload.contacts.withIndex()) {
                currentCoroutineContext().ensureActive()
                try {
                    var photoKey: String? = null
                    if (activeUser.uploadsEnabled) {
                        val photo = entry.photoBase64?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
                        if (photo != null && photo.isNotEmpty()) {
                            photoKey = appContainer.api.uploadVaultFile(
                                "${entry.contact.name.take(40).replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "contact" }}-photo.jpg",
                                entry.photoMime.ifBlank { "image/jpeg" }, photo.size.toLong(), { photo.inputStream() },
                            ).key
                        }
                    }
                    // The stable client ID makes a WorkManager retry update, rather than duplicate, a previously saved contact.
                    appContainer.api.saveContact(entry.contact.copy(photoKey = photoKey), isNew = true, idempotentCreate = true)
                    imported++
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (error is ApiException && (error.isOffline || error.status == 429 || error.status >= 500) && runAttemptCount < 5) return Result.retry()
                    failed++
                }
                val done = index + 1
                setProgress(progressData(done, total))
                setForeground(foregroundInfo(jobId, done, total))
            }
            try { appContainer.vault.refreshContacts() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* The contacts were saved; a later sync can refresh the local cache. */ }
            val output = workDataOf(
                ContactImportWork.OUTPUT_IMPORTED to imported,
                ContactImportWork.OUTPUT_FAILED to failed,
                ContactImportWork.OUTPUT_SKIPPED_DUPLICATES to payload.skippedDuplicates,
                ContactImportWork.OUTPUT_SKIPPED_INVALID to payload.skippedInvalid,
                ContactImportWork.OUTPUT_SKIPPED_REPEATED to payload.skippedRepeated,
            )
            postResultNotification(jobId, imported, failed, payload.skippedDuplicates + payload.skippedInvalid + payload.skippedRepeated)
            terminal = true
            Result.success(output)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.message?.take(180) ?: "The contact import couldn't be completed."
            terminal = runAttemptCount >= 5 || error !is ApiException || !(error.isOffline || error.status == 429 || error.status >= 500)
            if (!terminal && runAttemptCount < 5) Result.retry()
            else {
                terminal = true
                postFailureNotification(jobId, message)
                Result.failure(workDataOf(ContactImportWork.KEY_FAILURE to message))
            }
        } finally {
            if (terminal || isStopped) payloadFile.delete()
        }
    }

    private fun progressData(done: Int, total: Int) = workDataOf(
        ContactImportWork.KEY_DONE to done,
        ContactImportWork.KEY_TOTAL to total,
        ContactImportWork.KEY_PROGRESS to if (total == 0) 0 else (done * 100 / total),
    )

    private fun foregroundInfo(jobId: String, done: Int, total: Int): ForegroundInfo {
        val notification = ContactImportNotifications.progress(context, notificationId, done, total)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        return ForegroundInfo(notificationId, notification, serviceType)
    }

    private fun postResultNotification(jobId: String, imported: Int, failed: Int, skipped: Int) {
        val notification = ContactImportNotifications.result(context, jobId, imported, failed, skipped)
        ContactImportNotifications.post(context, notificationId xor 0x40000000, notification)
    }

    private fun postFailureNotification(jobId: String, message: String) {
        ContactImportNotifications.post(context, notificationId xor 0x40000000, ContactImportNotifications.failure(context, jobId, message))
    }
}

internal object ContactImportNotifications {
    private const val PROGRESS_CHANNEL = "contact-import-progress"
    private const val RESULTS_CHANNEL = "contact-import-results"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL, "Contact import progress", NotificationManager.IMPORTANCE_LOW).apply { description = "Shows background progress while contacts are imported into Persora." })
        manager.createNotificationChannel(NotificationChannel(RESULTS_CHANNEL, "Contact import updates", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Notifies you when a phone contact import finishes." })
    }

    fun progress(context: Context, id: Int, done: Int, total: Int): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Importing phone contacts")
            .setContentText(if (total > 0) "$done of $total contacts processed" else "Preparing your contacts")
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), done.coerceAtLeast(0), total == 0)
            .setContentIntent(openApp(context, id))
            .build()
    }

    fun result(context: Context, jobId: String, imported: Int, failed: Int, skipped: Int) = NotificationCompat.Builder(context, RESULTS_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("Phone contact import complete")
        .setContentText("$imported added${if (failed > 0) " · $failed couldn't be saved" else ""}${if (skipped > 0) " · $skipped skipped" else ""}")
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setAutoCancel(true)
        .setContentIntent(openApp(context, jobId.hashCode()))
        .build()

    fun failure(context: Context, jobId: String, message: String) = NotificationCompat.Builder(context, RESULTS_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("Phone contact import needs attention")
        .setContentText(message)
        .setCategory(NotificationCompat.CATEGORY_ERROR)
        .setAutoCancel(true)
        .setContentIntent(openApp(context, jobId.hashCode()))
        .build()

    fun post(context: Context, id: Int, notification: android.app.Notification) {
        ensureChannels(context)
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification) }
    }

    private fun openApp(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

private object ContactImportPayloadCipher {
    private const val KEY_ALIAS = "persora-phone-contact-import-v1"

    fun encrypt(context: Context, file: File, plaintext: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(plaintext)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            output.write(ciphertext)
        }
    }

    fun decrypt(context: Context, file: File): ByteArray {
        val bytes = file.readBytes()
        require(bytes.isNotEmpty()) { "The queued contact import is empty." }
        val ivLength = bytes[0].toInt() and 0xff
        require(ivLength in 12..16 && bytes.size > ivLength + 1) { "The queued contact import is damaged." }
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val ciphertext = bytes.copyOfRange(1 + ivLength, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }
}
