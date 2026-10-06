package app.persora.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Persora "Bento" design system — a 1:1 port of the shadcn/VengeanceUI Agent Bento Grid tokens.
 *
 *   light: page neutral-50 · cards white · ring rgba(0,0,0,.08) · text neutral-950 · muted neutral-500
 *   dark : page neutral-950 · cards neutral-900 · ring rgba(255,255,255,.05) · text neutral-50 · muted neutral-400
 *   primary = Persora web brand blue (#1a73e8 light · #8ab4f8 dark) for buttons, active nav and filled bars;
 *   accent colour otherwise lives in tiles, bars, pills and dots (cyan / violet / fuchsia / emerald / amber / sky / rose …).
 */

@Immutable
data class BentoColors(
    val isDark: Boolean,
    /** Page background (`bg-background`). */
    val bg: Color,
    /** Primary text (`text-foreground`). */
    val fg: Color,
    /** Card surface (`bg-white` / `dark:bg-neutral-900`). */
    val card: Color,
    /** Inner visual panel inside a FeatCard (`bg-background/50` / `dark:bg-neutral-950/50`). */
    val panel: Color,
    /** Soft fill (`bg-muted`). */
    val muted: Color,
    /** Secondary text (`text-muted-foreground`). */
    val mutedFg: Color,
    /** Tertiary text (`text-muted-foreground/60`). */
    val subtleFg: Color,
    /** Hairline (`border-border/50`). */
    val border: Color,
    /** Stronger hairline (`border-border`). */
    val borderStrong: Color,
    /** 1 px card ring (`shadow-[0_0_0_1px_rgba(0,0,0,0.08)]`). */
    val ring: Color,
    /** Brand primary (`bg-primary`) — Persora blue. */
    val primary: Color,
    val primaryFg: Color,
    /** Destructive (`text-rose-500` family). */
    val danger: Color,
    val dangerSoft: Color,
    val dangerLine: Color,
    /** Scrim behind sheets / dialogs. */
    val scrim: Color,
)

val BentoLight = BentoColors(
    isDark = false,
    bg = Color(0xFFFAFAFA), fg = Color(0xFF0A0A0A), card = Color(0xFFFFFFFF), panel = Color(0x80FAFAFA),
    muted = Color(0xFFF5F5F5), mutedFg = Color(0xFF737373), subtleFg = Color(0xFFA3A3A3),
    border = Color(0xFFEBEBEB), borderStrong = Color(0xFFD4D4D4), ring = Color(0x14000000),
    primary = Color(0xFF1A73E8), primaryFg = Color(0xFFFFFFFF),
    danger = Color(0xFFE11D48), dangerSoft = Color(0x26F43F5E), dangerLine = Color(0x59F43F5E),
    scrim = Color(0x66000000),
)

val BentoDark = BentoColors(
    isDark = true,
    bg = Color(0xFF0A0A0A), fg = Color(0xFFFAFAFA), card = Color(0xFF171717), panel = Color(0x800A0A0A),
    muted = Color(0xFF262626), mutedFg = Color(0xFFA3A3A3), subtleFg = Color(0xFF737373),
    border = Color(0xFF262626), borderStrong = Color(0xFF404040), ring = Color(0x0DFFFFFF),
    primary = Color(0xFF8AB4F8), primaryFg = Color(0xFF0B2A5C),
    danger = Color(0xFFFB7185), dangerSoft = Color(0x26F43F5E), dangerLine = Color(0x59F43F5E),
    scrim = Color(0x99000000),
)

/**
 * Live token access. [palette] is snapshot state: PersoraTheme sets it from the system colour mode, and any
 * composable that reads `Bento.fg` etc. recomposes automatically when it flips. Reads from plain Kotlin code
 * (draw lambdas, click handlers, activities) simply get the current palette.
 */
object Bento {
    var palette: BentoColors by mutableStateOf(BentoLight)
    val isDark: Boolean get() = palette.isDark
    val bg: Color get() = palette.bg
    val fg: Color get() = palette.fg
    val card: Color get() = palette.card
    val panel: Color get() = palette.panel
    val muted: Color get() = palette.muted
    val mutedFg: Color get() = palette.mutedFg
    val subtleFg: Color get() = palette.subtleFg
    val border: Color get() = palette.border
    val borderStrong: Color get() = palette.borderStrong
    val ring: Color get() = palette.ring
    val primary: Color get() = palette.primary
    val primaryFg: Color get() = palette.primaryFg
    /** `bg-primary/12` — active nav tab, selected rows, soft brand fills. */
    val primarySoft: Color get() = palette.primary.copy(alpha = if (palette.isDark) 0.18f else 0.12f)
    val danger: Color get() = palette.danger
    val dangerSoft: Color get() = palette.dangerSoft
    val dangerLine: Color get() = palette.dangerLine
    val scrim: Color get() = palette.scrim
    /** Hatch stroke used behind chart bars (`text-border/40`). */
    val hatch: Color get() = palette.borderStrong.copy(alpha = if (palette.isDark) 0.35f else 0.6f)
}

/** One Tailwind accent family (400 / 500 / 600) with the derived soft fills the component uses. */
@Immutable
data class Accent(val name: String, val c400: Color, val c500: Color, val c600: Color) {
    /** `text-x-600 dark:text-x-400` */
    val text: Color get() = if (Bento.isDark) c400 else c600
    /** `bg-x-500/15` */
    val soft: Color get() = c500.copy(alpha = if (Bento.isDark) 0.18f else 0.14f)
    /** `border-x-500/60` */
    val line: Color get() = c500.copy(alpha = 0.45f)
    /** 3-D tile fill: `bg-gradient-to-b from-x-400 to-x-600`. */
    val tile: Brush get() = Brush.verticalGradient(listOf(c400, c600))
    /** Bar fill: `bg-gradient-to-r from-x-500 to-x-400`. */
    val bar: Brush get() = Brush.horizontalGradient(listOf(c500, c400))
}

object Accents {
    /** Persora brand blue (web --p-blue): 400 #4285F4 · 500 #1A73E8 · 600 #174EA6. */
    val brand = Accent("brand", Color(0xFF4285F4), Color(0xFF1A73E8), Color(0xFF174EA6))
    val cyan = Accent("cyan", Color(0xFF22D3EE), Color(0xFF06B6D4), Color(0xFF0891B2))
    val sky = Accent("sky", Color(0xFF38BDF8), Color(0xFF0EA5E9), Color(0xFF0284C7))
    val blue = Accent("blue", Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF2563EB))
    val indigo = Accent("indigo", Color(0xFF818CF8), Color(0xFF6366F1), Color(0xFF4F46E5))
    val violet = Accent("violet", Color(0xFFA78BFA), Color(0xFF8B5CF6), Color(0xFF7C3AED))
    val fuchsia = Accent("fuchsia", Color(0xFFE879F9), Color(0xFFD946EF), Color(0xFFC026D3))
    val rose = Accent("rose", Color(0xFFFB7185), Color(0xFFF43F5E), Color(0xFFE11D48))
    val orange = Accent("orange", Color(0xFFFB923C), Color(0xFFF97316), Color(0xFFEA580C))
    val amber = Accent("amber", Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFD97706))
    val lime = Accent("lime", Color(0xFFA3E635), Color(0xFF84CC16), Color(0xFF65A30D))
    val emerald = Accent("emerald", Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669))
    val teal = Accent("teal", Color(0xFF2DD4BF), Color(0xFF14B8A6), Color(0xFF0D9488))
    val slate = Accent("slate", Color(0xFF94A3B8), Color(0xFF64748B), Color(0xFF475569))
    val zinc = Accent("zinc", Color(0xFFA1A1AA), Color(0xFF71717A), Color(0xFF52525B))

    val all = listOf(brand, cyan, sky, blue, indigo, violet, fuchsia, rose, orange, amber, lime, emerald, teal, slate, zinc)

    /** Maps the `color` string used by SECTION_DEFINITIONS / folder colours (web names) to a Tailwind accent. */
    fun byName(name: String): Accent = when (name.lowercase()) {
        "blue", "brand" -> brand; "sky" -> sky; "cyan" -> cyan; "teal" -> teal; "mint", "green" -> emerald; "lime" -> lime
        "red", "rose" -> rose; "orange", "peach" -> orange; "amber", "yellow" -> amber
        "purple", "violet", "lavender" -> violet; "fuchsia", "pink" -> fuchsia; "indigo" -> indigo
        "slate", "neutral", "gray", "grey" -> slate
        else -> zinc
    }
}

/** A foreground / fill / hairline trio used by soft icon boxes, pills and tags. Derived from an [Accent]. */
@Immutable
data class Tone(val fg: Color, val bg: Color, val line: Color, val accent: Accent = Accents.zinc)

fun Accent.tone(): Tone = Tone(fg = text, bg = soft, line = line, accent = this)

object Tones {
    val Blue: Tone get() = Accents.brand.tone()
    val Sky: Tone get() = Accents.sky.tone()
    val Cyan: Tone get() = Accents.cyan.tone()
    val Teal: Tone get() = Accents.teal.tone()
    val Green: Tone get() = Accents.emerald.tone()
    val Mint: Tone get() = Accents.emerald.tone()
    val Red: Tone get() = Accents.rose.tone()
    val Rose: Tone get() = Accents.rose.tone()
    val Orange: Tone get() = Accents.orange.tone()
    val Amber: Tone get() = Accents.amber.tone()
    val Yellow: Tone get() = Accents.amber.tone()
    val Purple: Tone get() = Accents.violet.tone()
    val Violet: Tone get() = Accents.violet.tone()
    val Fuchsia: Tone get() = Accents.fuchsia.tone()
    val Indigo: Tone get() = Accents.indigo.tone()
    val Slate: Tone get() = Accents.slate.tone()
    val Neutral: Tone get() = Tone(Bento.mutedFg, Bento.muted, Bento.borderStrong, Accents.zinc)

    fun byName(name: String): Tone = if (name.lowercase() in setOf("neutral", "gray", "grey", "")) Neutral else Accents.byName(name).tone()
}
