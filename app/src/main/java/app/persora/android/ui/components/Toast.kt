package app.persora.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import kotlinx.coroutines.delay

/** One transient message. `kind` = success | error | info. */
data class ToastMessage(val id: Long, val text: String, val kind: String)

/**
 * Persora's toast host (mirrors the website's `.toast-notice`): a floating card with a tone icon, auto-dismiss after
 * 2.6 s (4 s for errors), tap or swipe-down to dismiss, newest message replaces the previous one.
 */
class ToastState {
    var current by mutableStateOf<ToastMessage?>(null)
        private set
    private var counter = 0L

    fun show(text: String, kind: String = "info") {
        val clean = text.trim().ifBlank { return }
        val (friendly, friendlyKind) = humanizeError(clean, kind)
        // Don't stack the same connectivity notice over and over while polling.
        if (friendlyKind == "offline" && current?.kind == "offline") return
        current = ToastMessage(++counter, friendly, friendlyKind)
    }
    fun success(text: String) = show(text, "success")
    fun error(text: String) = show(text, "error")
    fun dismiss() { current = null }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier, bottomPadding: androidx.compose.ui.unit.Dp = 88.dp) {
    val message = state.current
    // Keep showing the last message during the exit animation.
    var shown by remember { mutableStateOf<ToastMessage?>(null) }
    LaunchedEffect(message) {
        if (message != null) { shown = message; delay(if (message.kind == "error") 4_000 else 2_600); if (state.current?.id == message.id) state.dismiss() }
    }
    Box(modifier.fillMaxSize().padding(bottom = bottomPadding, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = message != null, enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)), exit = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(180))) {
            val m = shown ?: return@AnimatedVisibility
            val (icon, tint, bg) = when (m.kind) {
                "success" -> Triple(Icons.Outlined.CheckCircle, Accents.emerald.c400, Accents.emerald.c500.copy(alpha = 0.2f))
                "error" -> Triple(Icons.Outlined.ErrorOutline, Accents.rose.c400, Accents.rose.c500.copy(alpha = 0.2f))
                "offline" -> Triple(Icons.Outlined.CloudOff, Accents.amber.c400, Accents.amber.c500.copy(alpha = 0.2f))
                else -> Triple(Icons.Outlined.Info, Accents.sky.c400, Accents.sky.c500.copy(alpha = 0.2f))
            }
            Row(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
                    .clip(RoundedCornerShape(14.dp)).background(Bento.fg).border(1.dp, Bento.bg.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .pointerInput(m.id) { detectDragGestures { change, drag -> if (drag.y > 12f) { change.consume(); state.dismiss() } } }
                    .clickable { state.dismiss() }.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(m.text, style = MaterialTheme.typography.bodyMedium, color = Bento.bg, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.Close, "Dismiss", tint = Bento.bg.copy(alpha = 0.6f), modifier = Modifier.size(28.dp).clip(CircleShape).clickable { state.dismiss() }.padding(6.dp))
            }
        }
    }
}

private val OFFLINE_HINTS = listOf("network error", "unable to resolve host", "failed to connect", "timed out", "timeout", "connection reset", "connection refused", "connection abort", "no address associated", "appear to be offline", "sockettimeout", "unknownhost", "ssl", "handshake", "econn", "enetunreach", "no route to host", "software caused")
private val SERVER_HINTS = listOf("persora service returned 5", "internal server error", "internal error", "bad gateway", "service unavailable", "gateway timeout", "worker threw", "cloudflare", "error 1101", "error 52", "error 53", "upstream", "too many requests", "temporarily unavailable", "unexpected end of stream", "unexpected json", "expected start of the object", "illegal input", "serialization")

/**
 * Turns raw transport / server failures into calm, human copy. Server-side validation messages
 * (already written for people — "Title is required.") pass through unchanged.
 */
fun humanizeError(message: String, kind: String): Pair<String, String> {
    val m = message.lowercase()
    return when {
        OFFLINE_HINTS.any { it in m } -> "You're offline. Showing your saved copy — changes will sync when you're back online." to "offline"
        SERVER_HINTS.any { it in m } -> "Persora's server is having a moment. Please try again in a few seconds." to "error"
        m.contains("sign in is required") || m.contains("session expired") || Regex("\\b401\\b").containsMatchIn(m) -> "Your session has ended. Please sign in again to continue." to "error"
        m.contains("persora service returned 4") -> "That request couldn't be completed. Please check the details and try again." to "error"
        else -> message to kind
    }
}

/** Heuristic for legacy `notify(message, isError)` callers: success wording → green, errors → red, otherwise blue. */
fun toastKindFor(message: String, isError: Boolean): String = when {
    isError -> "error"
    Regex("(saved|updated|deleted|removed|copied|sent|done|added|merged|enabled|complete|success|signed|unlocked|now your|restored|cleared|shared|snoozed|dismissed|exported|moved|pinned|marked)", RegexOption.IGNORE_CASE).containsMatchIn(message) -> "success"
    Regex("(could not|couldn't|can't|cannot|failed|invalid|required|must|needed|unable|wrong|denied|expired|too |not )", RegexOption.IGNORE_CASE).containsMatchIn(message) -> "error"
    else -> "info"
}
