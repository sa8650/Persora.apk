package app.persora.android.calls

import android.content.Context
import android.provider.CallLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

/** A single call — either from the system call log (every phone app) or Persora's own record of calls it placed/handled. */
@Serializable
data class CallEntry(
    val id: String,
    val number: String,
    val name: String? = null,
    val direction: String, // incoming | outgoing | missed | rejected
    val startedAt: Long,
    val durationSec: Long = 0,
    val sim: String? = null,
    val source: String = "persora",
)

/**
 * Persora's private call history (app-private JSON). The Calls screen merges it with the system call log when
 * READ_CALL_LOG is granted, so the list looks like a regular phone's "Recents" even for calls made elsewhere.
 */
object CallLogStore {
    private const val MAX = 500
    private val json = Json { ignoreUnknownKeys = true }
    val entries = MutableStateFlow<List<CallEntry>>(emptyList())
    private var loaded = false

    private fun file(context: Context) = File(context.applicationContext.filesDir, "persora-calls.json")

    fun load(context: Context): List<CallEntry> {
        if (!loaded) {
            entries.value = runCatching { json.decodeFromString(ListSerializer(CallEntry.serializer()), file(context).readText()) }.getOrDefault(emptyList())
            loaded = true
        }
        return entries.value
    }

    private fun save(context: Context, list: List<CallEntry>) {
        entries.value = list.sortedByDescending { it.startedAt }.take(MAX)
        runCatching { file(context).writeText(json.encodeToString(ListSerializer(CallEntry.serializer()), entries.value)) }
    }

    fun noteOutgoing(context: Context, number: String, name: String?, sim: String?) {
        val list = load(context)
        save(context, list + CallEntry("out-${System.currentTimeMillis()}", number, name, "outgoing", System.currentTimeMillis(), 0, sim))
    }

    fun noteEnded(
        context: Context,
        number: String,
        name: String?,
        incoming: Boolean,
        answered: Boolean,
        startedAt: Long,
        durationSec: Long
    ) {
        val list = load(context)
        val direction = if (incoming) {
            if (answered) "incoming" else "missed"
        } else {
            "outgoing"
        }

        val existingIndex = if (!incoming) {
            list.indexOfFirst {
                it.direction == "outgoing" &&
                        it.number.takeLast(7) == number.takeLast(7) &&
                        abs(it.startedAt - startedAt) < 120_000
            }
        } else -1

        val newList = if (existingIndex != -1) {
            list.mapIndexed { index, entry ->
                if (index == existingIndex) {
                    entry.copy(
                        name = entry.name ?: name,
                        durationSec = durationSec
                    )
                } else {
                    entry
                }
            }
        } else {
            val entry = CallEntry(
                id = "${if (incoming) "in" else "out"}-${System.currentTimeMillis()}",
                number = number,
                name = name,
                direction = direction,
                startedAt = startedAt,
                durationSec = durationSec
            )
            list + entry
        }

        save(context, newList)
    }

    fun clear(context: Context) = save(context, emptyList())

    /** System call log (requires READ_CALL_LOG). Returns null when not permitted. */
    fun systemLog(context: Context, limit: Int = 300): List<CallEntry>? {
        if (!Calls.hasCallLogPermission(context)) return null
        val out = mutableListOf<CallEntry>()
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID)
        runCatching {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
                val iId = c.getColumnIndex(CallLog.Calls._ID); val iNum = c.getColumnIndex(CallLog.Calls.NUMBER); val iName = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val iType = c.getColumnIndex(CallLog.Calls.TYPE); val iDate = c.getColumnIndex(CallLog.Calls.DATE); val iDur = c.getColumnIndex(CallLog.Calls.DURATION); val iAcc = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                while (c.moveToNext() && out.size < limit) {
                    val direction = when (c.getInt(iType)) {
                        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.VOICEMAIL_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> "rejected"
                        else -> "incoming"
                    }
                    out += CallEntry("sys-${c.getLong(iId)}", c.getString(iNum).orEmpty(), c.getString(iName)?.takeIf { it.isNotBlank() }, direction, c.getLong(iDate), c.getLong(iDur), c.getString(iAcc)?.takeIf { it.isNotBlank() }, "system")
                }
            }
        }
        return out
    }
}
