package app.persora.android.ui.boot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accent
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.components.IconTile
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native port of src/components/PersoraBootScreen.tsx + ui/orbiting-circles.tsx.
 * Three rings (radius 56 / 96 / 136, durations 19/1.12 s, 30/1.08 s reversed, 41/1.05 s) orbit a breathing ShieldCheck core.
 */
private data class OrbitItem(val icon: ImageVector, val accent: Accent, val label: String)

private val innerItems = listOf(
    OrbitItem(Icons.Outlined.Description, Accents.sky, "Documents"),
    OrbitItem(Icons.Outlined.MonitorHeart, Accents.rose, "Medical records"),
    OrbitItem(Icons.Outlined.ContactPage, Accents.sky, "Contacts"),
    OrbitItem(Icons.Outlined.EditNote, Accents.amber, "Notes"),
    OrbitItem(Icons.Outlined.NotificationsActive, Accents.sky, "Reminders"),
)
private val middleItems = listOf(
    OrbitItem(Icons.Outlined.Alarm, Accents.violet, "Alarms"),
    OrbitItem(Icons.Outlined.EventNote, Accents.violet, "Life timeline"),
    OrbitItem(Icons.Outlined.Wallet, Accents.sky, "Subscriptions and memberships"),
    OrbitItem(Icons.Outlined.Share, Accents.amber, "Shared documents"),
    OrbitItem(Icons.Outlined.BusinessCenter, Accents.sky, "Business cards"),
)
private val outerItems = listOf(
    OrbitItem(Icons.Outlined.School, Accents.sky, "Academics"),
    OrbitItem(Icons.Outlined.Cloud, Accents.sky, "Private cloud vault"),
    OrbitItem(Icons.Outlined.Link, Accents.violet, "Linked records"),
    OrbitItem(Icons.Outlined.MenuBook, Accents.rose, "Personal records"),
    OrbitItem(Icons.Outlined.Favorite, Accents.amber, "Health archive"),
)

@Composable
fun PersoraBootScreen(statusText: String = "Preparing your personal vault…") {
    Column(
        modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Bento.card, Bento.bg), radius = 1400f)).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(312.dp), contentAlignment = Alignment.Center) {
            OrbitRing(radius = 56.dp, durationMs = (19_000 / 1.12).toInt(), iconSize = 24.dp, items = innerItems)
            OrbitRing(radius = 96.dp, durationMs = (30_000 / 1.08).toInt(), iconSize = 32.dp, items = middleItems, reverse = true)
            OrbitRing(radius = 136.dp, durationMs = (41_000 / 1.05).toInt(), iconSize = 40.dp, items = outerItems)
            BootCore()
        }
        Spacer(Modifier.height(13.dp))
        Text("YOUR PRIVATE SPACE", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = Bento.primary)
        Text("Persora", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Bento.fg, letterSpacing = (-0.9).sp)
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
    }
}

@Composable
private fun OrbitRing(radius: Dp, durationMs: Int, iconSize: Dp, items: List<OrbitItem>, reverse: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "orbit-$radius")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    // Orbit path (stroke: --p-blue at 15 % opacity, 1 px)
    Canvas(Modifier.size(radius * 2)) { drawCircle(Bento.primary.copy(alpha = 0.15f), radius = radiusPx, center = Offset(size.width / 2, size.height / 2), style = Stroke(width = 1.dp.toPx())) }
    val count = items.size
    items.forEachIndexed { index, item ->
        val baseAngle = (360f / count) * index
        val sweep = if (reverse) -360f * progress else 360f * progress
        // CSS: rotate(angle) translateY(radius) → start at the bottom of the circle, rotate clockwise.
        val rad = Math.toRadians((baseAngle + sweep + 90f).toDouble())
        val dx = with(density) { (radiusPx * cos(rad)).toFloat().toDp() }
        val dy = with(density) { (radiusPx * sin(rad)).toFloat().toDp() }
        OrbitIcon(item, iconSize, Modifier.offset(x = dx, y = dy))
    }
}

@Composable
private fun OrbitIcon(item: OrbitItem, size: Dp, modifier: Modifier) {
    Box(modifier) { IconTile(item.icon, item.accent, size = size, radius = size * 0.35f, contentDescription = item.label, iconSize = size * 0.54f) }
}

@Composable
private fun BootCore() {
    val transition = rememberInfiniteTransition(label = "core")
    val breathe by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val ring by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ring")
    Box(contentAlignment = Alignment.Center) {
        // ::before expanding ring
        Box(Modifier.size((72 + 18).dp * (0.92f + 0.30f * ring)).border(1.dp, Bento.primary.copy(alpha = 0.18f * (1f - ring)), RoundedCornerShape(30.dp)))
        Box(
            Modifier.offset(y = (-2).dp * breathe).size(72.dp).shadow((10 + 4 * breathe).dp, RoundedCornerShape(24.dp), ambientColor = Bento.primary.copy(alpha = 0.24f), spotColor = Bento.primary.copy(alpha = 0.29f))
                .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Bento.primary, Bento.primary))).border(1.dp, Bento.bg.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.VerifiedUser, contentDescription = "Persora", tint = Bento.primaryFg, modifier = Modifier.size(35.dp)) }
    }
}
