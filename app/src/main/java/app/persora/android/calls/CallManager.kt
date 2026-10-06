package app.persora.android.calls

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot of the call Persora's in-call screen is showing. */
data class ActiveCall(
    val number: String,
    val state: Int,
    val incoming: Boolean,
    val startedAt: Long,
    val connectedAt: Long? = null,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val bluetooth: Boolean = false,
    val onHold: Boolean = false,
    val disconnectReason: String? = null,
) {
    val isRinging get() = state == Call.STATE_RINGING
    val isActive get() = state == Call.STATE_ACTIVE
    val isEnded get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
    val statusLabel: String get() = when (state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT -> "Calling…"
        Call.STATE_ACTIVE -> if (onHold) "On hold" else "Connected"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DISCONNECTING -> "Ending…"
        Call.STATE_DISCONNECTED -> disconnectReason ?: "Call ended"
        else -> "Connecting…"
    }
}

/** Bridges android.telecom.Call ↔ Compose. Single active call (conference/multi-call falls back to the first). */
object CallManager {
    val current = MutableStateFlow<ActiveCall?>(null)
    private var call: Call? = null
    private var appContext: Context? = null
    internal var service: PersoraInCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(c: Call, state: Int) = refresh()
        override fun onDetailsChanged(c: Call, details: Call.Details) = refresh()
    }

    fun onCallAdded(context: Context, c: Call) {
        appContext = context.applicationContext
        if (call != null && call != c) return // keep showing the first call; Telecom handles waiting calls
        call = c
        c.registerCallback(callback)
        val number = c.details?.handle?.schemeSpecificPart.orEmpty()
        val incoming = (Build.VERSION.SDK_INT >= 29 && c.details?.callDirection == Call.Details.DIRECTION_INCOMING) || stateOf(c) == Call.STATE_RINGING
        current.value = ActiveCall(number, stateOf(c), incoming, System.currentTimeMillis())
    }

    fun onCallRemoved(c: Call) {
        if (c != call) return
        c.unregisterCallback(callback)
        val snapshot = current.value
        val ctx = appContext
        if (snapshot != null && ctx != null) {
            val duration = snapshot.connectedAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
            val name = Calls.matchContact(snapshot.number, runCatching { ctx.let { (it.applicationContext as app.persora.android.PersoraApp).container.vault.contacts.value } }.getOrDefault(emptyList()))?.name
            CallLogStore.noteEnded(ctx, snapshot.number, name, snapshot.incoming, snapshot.connectedAt != null, snapshot.startedAt, duration)
        }
        call = null
        current.update { it?.copy(state = Call.STATE_DISCONNECTED) }
    }

    private fun stateOf(c: Call): Int = if (Build.VERSION.SDK_INT >= 31) c.details.state else @Suppress("DEPRECATION") c.state

    private fun refresh() {
        val c = call ?: return
        val state = stateOf(c)
        current.update { prev ->
            prev?.copy(
                state = state,
                connectedAt = prev.connectedAt ?: if (state == Call.STATE_ACTIVE) System.currentTimeMillis() else null,
                onHold = state == Call.STATE_HOLDING,
                disconnectReason = if (state == Call.STATE_DISCONNECTED) c.details?.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() } else null,
            )
        }
    }

    fun onAudioStateChanged(state: CallAudioState) {
        current.update { it?.copy(muted = state.isMuted, speaker = state.route == CallAudioState.ROUTE_SPEAKER, bluetooth = state.route == CallAudioState.ROUTE_BLUETOOTH) }
    }

    fun answer() = call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject() { call?.let { if (stateOf(it) == Call.STATE_RINGING) it.reject(false, null) else it.disconnect() } }
    fun hangup() = call?.disconnect()
    fun toggleMute() { val s = service ?: return; s.setMuted(!(current.value?.muted ?: false)) }
    fun toggleSpeaker() { val s = service ?: return; s.setAudioRoute(if (current.value?.speaker == true) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER) }
    fun toggleHold() { call?.let { if (stateOf(it) == Call.STATE_HOLDING) it.unhold() else it.hold() } }
    fun playDtmf(digit: Char) { call?.let { it.playDtmfTone(digit); it.stopDtmfTone() } }
    fun clearEnded() { if (call == null) current.value = null }
}
