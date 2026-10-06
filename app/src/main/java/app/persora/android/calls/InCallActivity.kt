package app.persora.android.calls

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.ui.contacts.ContactPhoto
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.MonoBody
import app.persora.android.ui.theme.MonoCaption
import app.persora.android.ui.theme.MonoStat
import app.persora.android.ui.theme.PersoraTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Persora's own in-call UI (default dialer role). Shows over the lock screen and keeps the display on while a call is live. */
class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) } else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { PersoraTheme { InCallScreen(onFinished = { finish() }) } }
    }

    companion object {
        fun intent(context: Context) = Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

// The call screen is always the dark bento surface (neutral-950 card stack), independent of the system theme —
// like every phone dialer, it has to be legible at arm's length and on the lock screen.
private val CallBg = Color(0xFF0A0A0A)
private val CallCard = Color(0xFF151515)
private val CallCardRaised = Color(0xFF1C1C1C)
private val CallBorder = Color(0xFF262626)
private val CallBorderStrong = Color(0xFF3A3A3A)
private val CallFg = Color(0xFFFAFAFA)
private val CallMuted = Color(0xFFA3A3A3)
private val CallSubtle = Color(0xFF6B6B6B)
private val Brand = Accents.brand.c500
private val BrandLight = Color(0xFF8AB4F8)
private val CallGreen = Accents.emerald.c500
private val CallRed = Accents.rose.c500

private enum class CallPhase { Incoming, Dialing, Connected, Hold, Ended }

@Composable
fun InCallScreen(onFinished: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val call by CallManager.current.collectAsStateWithLifecycle()
    val contacts by remember { runCatching { context.appContainer.vault.contacts }.getOrNull() ?: kotlinx.coroutines.flow.MutableStateFlow<List<app.persora.android.data.model.PersoraContact>>(emptyList()) }.collectAsStateWithLifecycle()
    val contact = remember(call?.number, contacts) { Calls.matchContact(call?.number, contacts) }
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var elapsed by remember { mutableStateOf(0L) }

    // Close ~1.5 s after the call ends so the user sees "Call ended" and the duration.
    LaunchedEffect(call?.state) {
        if (call == null) { onFinished(); return@LaunchedEffect }
        if (call?.isEnded == true) { delay(1500); CallManager.clearEnded(); onFinished() }
    }
    LaunchedEffect(call?.connectedAt) { while (true) { elapsed = call?.connectedAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L; delay(1000) } }

    val c = call
    val phase = when {
        c == null || c.isEnded -> CallPhase.Ended
        c.isRinging -> CallPhase.Incoming
        c.isActive && !c.onHold -> CallPhase.Connected
        c.isActive || c.onHold || c.state == android.telecom.Call.STATE_HOLDING -> CallPhase.Hold
        else -> CallPhase.Dialing
    }
    val phaseAccent = when (phase) { CallPhase.Incoming -> CallGreen; CallPhase.Ended -> CallRed; CallPhase.Hold -> Accents.amber.c500; else -> Brand }
    val phaseLabel = when (phase) { CallPhase.Incoming -> "INCOMING"; CallPhase.Dialing -> "DIALING"; CallPhase.Connected -> "LIVE"; CallPhase.Hold -> "ON HOLD"; CallPhase.Ended -> "ENDED" }

    Box(Modifier.fillMaxSize().background(CallBg).callDotGrid()) {
        // Soft brand glow behind the avatar stage; breathes slowly while the call is live.
        val glow = rememberInfiniteTransition(label = "glow").animateFloat(0.55f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "glowA")
        Box(Modifier.fillMaxWidth().height(420.dp).align(Alignment.TopCenter).background(Brush.radialGradient(listOf(phaseAccent.copy(alpha = 0.22f * glow.value), Color.Transparent), radius = 620f)))

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            // Header strip — bento card header: live dot + mono label + status pill.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CallCard).border(1.dp, CallBorder, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                PulseDot(phaseAccent, animate = phase != CallPhase.Ended)
                Spacer(Modifier.width(8.dp))
                Text("PERSORA CALL", style = MonoCaption, color = CallMuted)
                Spacer(Modifier.weight(1f))
                Text(if (c?.incoming == true) "IN · ${phaseLabel}" else "OUT · ${phaseLabel}", style = MonoCaption, color = phaseAccent, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(phaseAccent.copy(alpha = 0.14f)).border(1.dp, phaseAccent.copy(alpha = 0.45f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
            }

            Spacer(Modifier.height(26.dp))
            AvatarStage(phase, phaseAccent) {
                if (contact != null) ContactPhoto(contact, 104.dp)
                else Box(Modifier.size(104.dp).clip(CircleShape).background(CallCardRaised).border(1.dp, CallBorderStrong, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = CallFg, modifier = Modifier.size(46.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Text(contact?.name ?: c?.number?.ifBlank { null } ?: "Unknown number", color = CallFg, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, letterSpacing = (-0.4).sp)
            if (contact != null) Text(c?.number.orEmpty(), style = MonoBody, color = CallMuted, modifier = Modifier.padding(top = 2.dp))

            Spacer(Modifier.height(14.dp))
            // Status panel: mono timer + animated waveform (bento "token monitor" card).
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CallCard).border(1.dp, CallBorder, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when (phase) { CallPhase.Connected -> "DURATION"; CallPhase.Hold -> "ON HOLD"; CallPhase.Incoming -> "RINGING"; CallPhase.Dialing -> "CONNECTING"; CallPhase.Ended -> "CALL ENDED" }, style = MonoCaption, color = CallSubtle)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when {
                                phase == CallPhase.Connected || phase == CallPhase.Hold -> Calls.formatDuration(elapsed)
                                phase == CallPhase.Ended && c?.connectedAt != null -> Calls.formatDuration(elapsed)
                                phase == CallPhase.Ended -> c?.statusLabel ?: "Call ended"
                                else -> c?.statusLabel.orEmpty()
                            },
                            style = MonoStat.copy(fontSize = 30.sp, lineHeight = 34.sp), color = CallFg,
                        )
                    }
                    Waveform(active = phase == CallPhase.Connected, dialing = phase == CallPhase.Dialing || phase == CallPhase.Incoming, color = phaseAccent, modifier = Modifier.width(132.dp).height(40.dp))
                }
                if (typed.isNotBlank()) {
                    Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(CallBorder)); Spacer(Modifier.height(8.dp))
                    Text(typed, style = MonoBody.copy(fontSize = 15.sp, letterSpacing = 2.sp), color = BrandLight)
                }
            }

            Spacer(Modifier.weight(1f))

            if (c != null && !c.isRinging && !c.isEnded) {
                AnimatedVisibility(visible = keypad, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) { Keypad(onDigit = { d -> typed += d; CallManager.playDtmf(d) }) }
                if (!keypad) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CallTile(if (c.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, "Mute", c.muted, Modifier.weight(1f), 0) { CallManager.toggleMute() }
                        CallTile(Icons.Outlined.Dialpad, "Keypad", false, Modifier.weight(1f), 40) { keypad = true }
                        CallTile(Icons.Outlined.VolumeUp, "Speaker", c.speaker, Modifier.weight(1f), 80) { CallManager.toggleSpeaker() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CallTile(if (c.onHold) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (c.onHold) "Resume" else "Hold", c.onHold, Modifier.weight(1f), 120) { CallManager.toggleHold() }
                        CallTile(Icons.Outlined.PersonAdd, "Contacts", false, Modifier.weight(1f), 160) { context.startActivity(Intent(context, app.persora.android.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        CallTile(Icons.Outlined.Bluetooth, "Bluetooth", c.bluetooth, Modifier.weight(1f), 200) { CallManager.service?.setAudioRoute(if (c.bluetooth) android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE else android.telecom.CallAudioState.ROUTE_BLUETOOTH) }
                    }
                } else TextButton(onClick = { keypad = false }) { Text("HIDE KEYPAD", style = MonoCaption, color = CallMuted) }
            }

            Spacer(Modifier.height(22.dp))
            when (phase) {
                CallPhase.Ended -> Spacer(Modifier.height(84.dp))
                CallPhase.Incoming -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigCallButton(Icons.Outlined.CallEnd, "Decline", CallRed, pulse = false) { CallManager.reject() }
                    BigCallButton(Icons.Outlined.Call, "Answer", CallGreen, pulse = true) { CallManager.answer() }
                }
                else -> BigCallButton(Icons.Outlined.CallEnd, "End call", CallRed, pulse = false) { CallManager.hangup() }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** Dark variant of the bento dot grid (Common.kt's version follows the light/dark app tokens). */
private fun Modifier.callDotGrid(spacing: Float = 16f): Modifier = drawBehind {
    val step = spacing * density; val col = Color.White.copy(alpha = 0.07f)
    var y = step / 2
    while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(col, radius = 1.1f * density, center = Offset(x, y)); x += step }; y += step }
}

@Composable
private fun PulseDot(color: Color, animate: Boolean) {
    val t = rememberInfiniteTransition(label = "dot").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "dotT")
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        if (animate) Box(Modifier.size(14.dp).scale(0.4f + t.value * 0.6f).clip(CircleShape).background(color.copy(alpha = (1f - t.value) * 0.6f)))
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    }
}

/**
 * Avatar with animated surroundings: expanding sonar rings while ringing/dialing, a slow rotating dashed
 * orbit while connected, everything still once the call has ended.
 */
@Composable
private fun AvatarStage(phase: CallPhase, accent: Color, content: @Composable () -> Unit) {
    val inf = rememberInfiniteTransition(label = "stage")
    val sonar = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "sonar")
    val orbit = inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "orbit")
    val breathe = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "breathe")
    val ringing = phase == CallPhase.Incoming || phase == CallPhase.Dialing
    val live = phase == CallPhase.Connected || phase == CallPhase.Hold
    val ringStrength by animateFloatAsState(if (ringing) 1f else 0f, tween(500), label = "ringStrength")
    val orbitStrength by animateFloatAsState(if (live) 1f else 0f, tween(600), label = "orbitStrength")

    Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2, size.height / 2)
            val base = 52.dp.toPx()
            val maxR = size.minDimension / 2
            // Sonar rings (three, staggered by a third of a cycle).
            if (ringStrength > 0.01f) for (i in 0 until 3) {
                val p = (sonar.value + i / 3f) % 1f
                drawCircle(accent.copy(alpha = (1f - p) * 0.45f * ringStrength), radius = base + (maxR - base) * p, center = centre, style = Stroke(width = (2.5f - p * 1.5f) * density))
            }
            // Static hairline halo + dashed brand orbit that slowly rotates while connected.
            drawCircle(Color.White.copy(alpha = 0.08f), radius = base + 12.dp.toPx(), center = centre, style = Stroke(1f * density))
            if (orbitStrength > 0.01f) {
                val r = base + 20.dp.toPx() + breathe.value * 3.dp.toPx()
                rotate(orbit.value, centre) {
                    drawCircle(accent.copy(alpha = 0.55f * orbitStrength), radius = r, center = centre, style = Stroke(width = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 10f * density)), cap = StrokeCap.Round))
                    // Orbiting marker dot
                    drawCircle(accent.copy(alpha = orbitStrength), radius = 3.5f * density, center = Offset(centre.x + r, centre.y))
                }
                rotate(-orbit.value * 0.6f, centre) {
                    drawCircle(Color.White.copy(alpha = 0.18f * orbitStrength), radius = r + 14.dp.toPx(), center = centre, style = Stroke(width = 1f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * density, 14f * density)), cap = StrokeCap.Round))
                }
            }
        }
        val scaleAvatar = when { live -> 1f + breathe.value * 0.02f; ringing -> 1f + sin(sonar.value * 2 * PI).toFloat() * 0.03f; else -> 1f }
        Box(Modifier.scale(scaleAvatar).clip(CircleShape).border(2.dp, accent.copy(alpha = if (phase == CallPhase.Ended) 0.3f else 0.9f), CircleShape).padding(3.dp)) { content() }
    }
}

/** Bars that "listen" while the call is live, tick gently while dialing/ringing and go flat on hold/ended. */
@Composable
private fun Waveform(active: Boolean, dialing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val phase = rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "waveP")
    val energy by animateFloatAsState(if (active) 1f else if (dialing) 0.35f else 0f, tween(500), label = "energy")
    val bars = 18
    Canvas(modifier) {
        val gap = 3f * density
        val w = (size.width - gap * (bars - 1)) / bars
        val mid = size.height / 2
        for (i in 0 until bars) {
            val seed = sin(i * 1.7f) * 0.5f + 0.5f
            val wave = abs(sin(phase.value + i * 0.55f)) * (0.35f + seed * 0.65f)
            val h = (size.height * (0.1f + wave * 0.9f * energy)).coerceAtLeast(3f * density)
            val x = i * (w + gap)
            val a = if (energy < 0.05f) 0.25f else 0.35f + wave * 0.65f
            drawRoundRect(color.copy(alpha = a), topLeft = Offset(x, mid - h / 2), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2))
        }
    }
}

@Composable
private fun CallTile(icon: ImageVector, label: String, active: Boolean, modifier: Modifier = Modifier, enterDelay: Int = 0, onClick: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(enterDelay.toLong()); shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(360), label = "appear")
    val bg by animateColorAsState(if (active) Brand.copy(alpha = 0.22f) else CallCard, tween(220), label = "tileBg")
    val stroke by animateColorAsState(if (active) Brand else CallBorder, tween(220), label = "tileBorder")
    Column(
        modifier.height(74.dp).scale(0.92f + appear * 0.08f).clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, stroke, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = if (active) BrandLight else CallFg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), style = MonoCaption, color = if (active) BrandLight else CallMuted)
    }
}

@Composable
private fun BigCallButton(icon: ImageVector, label: String, color: Color, pulse: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "big").animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "bigT")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            if (pulse) {
                Box(Modifier.size(96.dp).scale(0.72f + t.value * 0.28f).clip(CircleShape).background(color.copy(alpha = (1f - t.value) * 0.35f)))
                Box(Modifier.size(96.dp).scale(0.72f + ((t.value + 0.5f) % 1f) * 0.28f).clip(CircleShape).background(color.copy(alpha = (1f - (t.value + 0.5f) % 1f) * 0.25f)))
            }
            Box(Modifier.size(70.dp).scale(if (pulse) 1f + sin(t.value * 2 * PI).toFloat() * 0.03f else 1f).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(2.dp)); Text(label.uppercase(), style = MonoCaption, color = CallMuted)
    }
}

/** Phone-style 3×4 keypad; used in-call for DTMF and in the Calls screen to dial. */
@Composable
fun Keypad(onDigit: (Char) -> Unit, light: Boolean = false, modifier: Modifier = Modifier) {
    val rows = listOf(listOf('1' to "", '2' to "ABC", '3' to "DEF"), listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"), listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"), listOf('*' to "", '0' to "+", '#' to ""))
    val fg = if (light) app.persora.android.ui.theme.Bento.fg else CallFg
    val bg = if (light) app.persora.android.ui.theme.Bento.card else CallCard
    val stroke = if (light) app.persora.android.ui.theme.Bento.border else CallBorder
    val shape = RoundedCornerShape(16.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (digit, letters) ->
                    Column(Modifier.size(width = 84.dp, height = 62.dp).clip(shape).background(bg).border(1.dp, stroke, shape).clickable { onDigit(digit) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(digit.toString(), color = fg, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                        if (letters.isNotBlank()) Text(letters, style = MonoCaption, color = fg.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}
