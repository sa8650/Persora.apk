package app.persora.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.persora.android.ui.theme.Accent
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.MonoBody
import app.persora.android.ui.theme.MonoCaption
import app.persora.android.ui.theme.Tone
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.flow.first

/* ═══════════════════════════════════════════════════════════════════════════════════════════
   Bento primitives — a Compose port of the VengeanceUI "Agent Bento Grid" building blocks.
   ═══════════════════════════════════════════════════════════════════════════════════════════ */

/**
 * The bento card: `rounded-[20px] p-4 bg-white dark:bg-neutral-900` with the 1 px ring + 2 px shadow
 * (`shadow-[0_0_0_1px_rgba(0,0,0,.08),0_2px_4px_rgba(0,0,0,.04)]`, dark: inset top highlight + white/5 ring).
 */
@Composable
fun BentoCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp, radius: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .shadow(if (Bento.isDark) 0.dp else 1.5.dp, shape, ambientColor = Color(0x0A000000), spotColor = Color(0x14000000))
            .clip(shape).background(Bento.card).border(1.dp, Bento.ring, shape)
            .then(if (Bento.isDark) Modifier.drawBehind { drawLine(Color.White.copy(alpha = 0.05f), Offset(radius.toPx(), 0.5f), Offset(size.width - radius.toPx(), 0.5f), strokeWidth = 1f) } else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
        content = content,
    )
}

/**
 * FeatCard: title + description header, then a recessed visual panel
 * (`rounded-[14px] border border-border/50 bg-background/50`) that fills the rest of the card.
 */
@Composable
fun FeatCard(title: String, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, panelPadding: Dp = 10.dp, panelHeight: Dp? = null, content: @Composable BoxScope.() -> Unit) {
    BentoCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth(0.92f))
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth().then(if (panelHeight != null) Modifier.height(panelHeight) else Modifier), padding = panelPadding, content = content)
    }
}

/** The recessed inner panel of a FeatCard. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 10.dp, radius: Dp = 14.dp, dotted: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(Bento.panel).border(1.dp, Bento.border, shape).then(if (dotted) Modifier.dotGrid() else Modifier).padding(padding), content = content)
}

/** `<pattern>` of 0.75 px dots every 16 px (Card1 background). */
fun Modifier.dotGrid(spacing: Dp = 14.dp): Modifier = drawBehind {
    val step = spacing.toPx(); val c = Bento.borderStrong.copy(alpha = if (Bento.isDark) 0.35f else 0.8f)
    var y = step / 2
    while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(c, radius = 1.1f, center = Offset(x, y)); x += step }; y += step }
}

/** `repeating-linear-gradient(45deg, transparent 6px, currentColor 7px)` — the hatched chart track. */
fun Modifier.hatch(): Modifier = drawBehind {
    val c = Bento.hatch; val gap = 7.dp.toPx(); val w = size.width; val h = size.height
    var d = -h
    while (d < w) { drawLine(c, Offset(d, h), Offset(d + h, 0f), strokeWidth = 1f); d += gap }
}

/**
 * The 3-D accent tile (`bg-gradient-to-b from-x-400 to-x-600 border-x-600` with inset top highlight and
 * layered drop shadow) used for pipeline nodes, tool icons and namespaces. White glyph.
 */
@Composable
fun IconTile(icon: ImageVector, accent: Accent, size: Dp = 28.dp, radius: Dp = 8.dp, contentDescription: String? = null, iconSize: Dp = size * 0.5f) {
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.size(size).shadow(3.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000)).clip(shape)
            .background(accent.tile).border(1.dp, accent.c600, shape)
            .drawBehind {
                // inset_0_0.5px_0_0_rgba(255,255,255,.6) + inset_0_2px_6px_0_rgba(255,255,255,.3)
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.10f)))
                drawLine(Color.White.copy(alpha = 0.6f), Offset(radius.toPx() * 0.7f, 1f), Offset(this.size.width - radius.toPx() * 0.7f, 1f), strokeWidth = 1.2f)
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

/** Soft tile: `bg-x-500/10 border-x-500/60 text-x-600` — the quieter sibling of [IconTile]. */
@Composable
fun ToneIconBox(icon: ImageVector, tone: Tone, size: Dp = 38.dp, radius: Dp = 12.dp, contentDescription: String? = null) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(tone.bg).border(1.dp, tone.line, RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription, tint = tone.fg, modifier = Modifier.size(size * 0.48f))
    }
}

/** `text-[8px] font-mono uppercase tracking-widest text-muted-foreground` */
@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = Bento.mutedFg) {
    Text(text.uppercase(), style = MonoCaption, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Eyebrow(text: String, color: Color = Bento.mutedFg) = MonoLabel(text, color = color)

/** `rounded px-1 py-0.5 bg-x-500/15 text-x-400 font-mono uppercase` status badge / tag. */
@Composable
fun Pill(text: String, tone: Tone = Tones.Neutral, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(6.dp)).background(tone.bg).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, null, tint = tone.fg, modifier = Modifier.size(11.dp))
        Text(text.uppercase(), style = MonoCaption.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold), color = tone.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Pulsing live dot (`bg-emerald-400` + expanding `bg-emerald-400/40` ring). */
@Composable
fun LiveDot(accent: Accent = Accents.emerald, label: String? = null) {
    val t = rememberInfiniteTransition(label = "live")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size((5 + 7 * pulse).dp).clip(CircleShape).background(accent.c400.copy(alpha = 0.4f * (1 - pulse))))
            Box(Modifier.size(5.dp).clip(CircleShape).background(accent.c400))
        }
        if (label != null) MonoLabel(label)
    }
}

/** `h-1.5 bg-muted/30 rounded-full` track with a gradient fill (and the sliding sheen when `shimmer`). */
@Composable
fun ProgressTrack(fraction: Float, accent: Accent, modifier: Modifier = Modifier, height: Dp = 6.dp, shimmer: Boolean = false) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Bento.muted)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(accent.bar)) {
            if (shimmer) {
                val t = rememberInfiniteTransition(label = "sheen")
                val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "x")
                Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent), startX = size.width * x, endX = size.width * (x + 0.6f))) })
            }
        }
    }
}

/** Avatar circle in muted mono style (initials). */
@Composable
fun Avatar(initials: String, size: Dp = 36.dp, tone: Tone = Tones.Neutral) {
    Box(Modifier.size(size).clip(CircleShape).background(tone.bg).border(1.dp, tone.line, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.SemiBold, color = tone.fg, fontFamily = app.persora.android.ui.theme.Mono)
    }
}

@Composable
fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Outlined.Inbox, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, Accents.zinc, size = 48.dp, radius = 14.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Bento.fg, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** shadcn `Button` default: monochrome primary, rounded-xl, 40 dp. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = modifier.height(42.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp), elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = Bento.primary, contentColor = Bento.primaryFg, disabledContainerColor = Bento.primary.copy(alpha = 0.45f), disabledContentColor = Bento.primaryFg)) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Bento.primaryFg, strokeWidth = 2.dp)
        else { if (icon != null) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }; Text(text, style = MaterialTheme.typography.labelLarge) }
    }
}

/** shadcn `variant="outline"`. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(38.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Bento.fg, containerColor = Bento.card), border = androidx.compose.foundation.BorderStroke(1.dp, Bento.borderStrong)) {
        if (icon != null) { Icon(icon, null, Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** shadcn `variant="secondary"`. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(onClick = onClick, modifier = modifier.height(38.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp), elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = Bento.muted, contentColor = Bento.fg)) {
        if (icon != null) { Icon(icon, null, Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionHeading(kicker: String, title: String, count: Int? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MonoLabel(kicker)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Bento.fg)
                if (count != null) CountBadge(count)
            }
        }
        trailing?.invoke()
    }
}

/** `font-mono text-[9px] bg-muted rounded px-1.5` counter. */
@Composable
fun CountBadge(count: Int, tone: Tone = Tones.Neutral) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(tone.bg).padding(horizontal = 5.dp, vertical = 1.dp)) { Text(if (count > 999) "999+" else "$count", style = MonoBody.copy(fontSize = 9.5.sp), color = tone.fg) }
}

@Composable
fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        MonoLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Bento.fg)
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirmLabel: String = "Delete", destructive: Boolean = true, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp), containerColor = Bento.card, tonalElevation = 0.dp,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Bento.fg) }, text = { Text(body, color = Bento.mutedFg) },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) Bento.danger else Bento.fg)) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Bento.mutedFg) } },
    )
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Bento.fg, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
}

/** shadcn `Tabs` list: muted track, card-coloured active segment with ring. */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(Bento.muted).padding(3.dp)) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (active) Bento.card else Color.Transparent)
                    .then(if (active) Modifier.border(1.dp, Bento.ring, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onSelect(index) }.padding(vertical = 7.dp), contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Bento.primary else Bento.mutedFg, maxLines = 1) }
        }
    }
}

/** Thin 1 px divider (`border-border/50`). */
@Composable
fun HairLine(modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(Bento.border)) }

/**
 * Bottom drawer that hosts a full record view (item / contact). Opens expanded; a short swipe down only settles
 * to the half-height stop, a second swipe (or the handle / scrim / back) closes it. No wasted space at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(onDismiss: () -> Unit, heightFraction: Float = 0.96f, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // ModalBottomSheet's own show() targets the half stop first; redirect that animation straight to fully expanded.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { state.targetValue }.first { it != SheetValue.Hidden }
        state.expand()
    }
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = Color.Transparent, scrimColor = Bento.scrim,
        shape = shape, dragHandle = null, contentWindowInsets = { WindowInsets(0.dp) },
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(heightFraction).clip(shape).background(Bento.bg).border(1.dp, Bento.ring, shape)) {
            content()
            Box(Modifier.align(Alignment.TopCenter).padding(top = 7.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Bento.borderStrong))
        }
    }
}

/** Fades a composable in when it first appears (`initial opacity 0, y 16 → 1, 0`). */
@Composable
fun Modifier.riseIn(delayMs: Int = 0): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(delayMs.toLong()); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(450), label = "rise-a")
    val y by animateFloatAsState(if (shown) 0f else 16f, tween(450), label = "rise-y")
    return this.alpha(a).offset(y = y.dp)
}


/** Compact bento search box: 40 dp, rounded-12, card surface with hairline, search glyph + clear button. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(shape).background(Bento.card).border(1.dp, Bento.borderStrong.copy(alpha = 0.8f), shape).padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = Bento.mutedFg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicTextField(
            value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = Bento.fg),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Bento.primary),
            modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Bento.subtleFg, maxLines = 1, overflow = TextOverflow.Ellipsis); inner() } },
        )
        if (value.isNotEmpty()) IconButton(onClick = { onChange("") }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "Clear", tint = Bento.mutedFg, modifier = Modifier.size(15.dp)) }
    }
}
