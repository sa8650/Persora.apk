package app.persora.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Google Keep-style note backgrounds. Stored in note metadata as `color` (name); "" = default card surface.
 * Light / dark pairs follow Keep's own palette so the same note looks right in both themes.
 */
object NoteColors {
    data class Swatch(val id: String, val label: String, val light: Color, val dark: Color)

    val all = listOf(
        Swatch("", "Default", Color.Unspecified, Color.Unspecified),
        Swatch("coral", "Coral", Color(0xFFFAAFA8), Color(0xFF77172E)),
        Swatch("peach", "Peach", Color(0xFFF39F76), Color(0xFF692B17)),
        Swatch("sand", "Sand", Color(0xFFFFF8B8), Color(0xFF7C4A03)),
        Swatch("mint", "Mint", Color(0xFFE2F6D3), Color(0xFF264D3B)),
        Swatch("sage", "Sage", Color(0xFFB4DDD3), Color(0xFF0C625D)),
        Swatch("fog", "Fog", Color(0xFFD4E4ED), Color(0xFF256377)),
        Swatch("storm", "Storm", Color(0xFFAECCDC), Color(0xFF284255)),
        Swatch("dusk", "Dusk", Color(0xFFD3BFDB), Color(0xFF472E5B)),
        Swatch("blossom", "Blossom", Color(0xFFF6E2DD), Color(0xFF6C394F)),
        Swatch("clay", "Clay", Color(0xFFE9E3D4), Color(0xFF4B443A)),
        Swatch("chalk", "Chalk", Color(0xFFEFEFF1), Color(0xFF232427)),
    )

    fun swatch(id: String?): Swatch? = all.firstOrNull { it.id == id.orEmpty() && it.id.isNotBlank() }

    /** Background for a note, or null for the default card surface. */
    fun background(id: String?): Color? = swatch(id)?.let { if (Bento.isDark) it.dark else it.light }

    /** The dot shown in the palette picker (what the colour looks like in the current theme). */
    fun dot(s: Swatch): Color? = if (s.id.isBlank()) null else if (Bento.isDark) s.dark else s.light

    /** Text on tinted notes stays near-black in light mode and near-white in dark mode. */
    val onTint: Color get() = if (Bento.isDark) Color(0xFFF5F5F5) else Color(0xFF1F1F1F)
    val onTintMuted: Color get() = if (Bento.isDark) Color(0xFFD4D4D4) else Color(0xFF4A4A4A)
}
