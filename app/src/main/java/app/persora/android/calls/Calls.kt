package app.persora.android.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import app.persora.android.data.model.PersoraContact

/** One SIM / calling account the phone can place calls from (what the system "Choose SIM" sheet lists). */
data class SimOption(val handle: PhoneAccountHandle, val label: String, val description: String, val color: Int)

/** In-app call-log and dialpad helpers. Call actions hand off to Android's system call UI; Persora has no in-call screen or default-dialer role. */
object Calls {
    val CALL_PERMISSIONS = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)

    fun hasCallPermission(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    fun hasCallLogPermission(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun telecom(context: Context): TelecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    /** Every call-capable SIM. Empty when READ_PHONE_STATE is missing or the device has no telephony. */
    fun simOptions(context: Context): List<SimOption> = try {
        val tm = telecom(context)
        tm.callCapablePhoneAccounts.mapNotNull { handle ->
            val account = tm.getPhoneAccount(handle) ?: return@mapNotNull null
            SimOption(handle, account.label?.toString()?.ifBlank { null } ?: "SIM", account.shortDescription?.toString().orEmpty(), account.highlightColor)
        }
    } catch (_: SecurityException) { emptyList() } catch (_: Exception) { emptyList() }

    /** The SIM the user marked as "always ask" (null) or preferred in system settings — same logic every phone app uses. */
    fun defaultSim(context: Context): PhoneAccountHandle? = try { telecom(context).getDefaultOutgoingPhoneAccount("tel") } catch (_: Exception) { null }

    fun normalize(number: String): String = number.filter { it.isDigit() || it == '+' || it == '#' || it == '*' }

    /** Hands the call to Telecom; the Android system's configured phone app supplies the in-call UI. */
    fun place(context: Context, number: String, sim: PhoneAccountHandle?, displayName: String? = null) {
        val clean = normalize(number)
        require(clean.isNotBlank()) { "That contact has no phone number." }
        val extras = Bundle().apply { sim?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) } }
        CallLogStore.noteOutgoing(context, clean, displayName, sim?.let { h -> simOptions(context).firstOrNull { it.handle == h }?.label })
        telecom(context).placeCall(Uri.fromParts("tel", clean, null), extras)
    }

    /** Match a dialled/incoming number to a Persora contact (last 7+ digits, like the system contacts matcher). */
    fun matchContact(number: String?, contacts: List<PersoraContact>): PersoraContact? {
        val digits = number?.filter { it.isDigit() } ?: return null
        if (digits.length < 5) return null
        return contacts.firstOrNull { c -> c.phoneNumbers.any { p -> val d = p.number.filter { it.isDigit() }; d.isNotEmpty() && (d.takeLast(7) == digits.takeLast(7) && (d.endsWith(digits) || digits.endsWith(d) || d.takeLast(9) == digits.takeLast(9))) } }
    }

    fun formatDuration(seconds: Long): String = if (seconds >= 3600) String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60) else String.format("%02d:%02d", seconds / 60, seconds % 60)
}
