package app.persora.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private fun schemeFor(c: BentoColors) = if (c.isDark) darkColorScheme(
    primary = c.primary, onPrimary = c.primaryFg, primaryContainer = c.primary.copy(alpha = 0.15f), onPrimaryContainer = c.primary,
    secondary = c.mutedFg, onSecondary = c.bg, secondaryContainer = c.muted, onSecondaryContainer = c.fg,
    tertiary = Accents.violet.c400, onTertiary = c.bg, tertiaryContainer = Accents.violet.soft, onTertiaryContainer = Accents.violet.c400,
    error = c.danger, onError = c.bg, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
    background = c.bg, onBackground = c.fg, surface = c.card, onSurface = c.fg, surfaceVariant = c.muted, onSurfaceVariant = c.mutedFg,
    surfaceContainer = c.card, surfaceContainerLow = c.card, surfaceContainerLowest = c.bg, surfaceContainerHigh = c.muted, surfaceContainerHighest = c.borderStrong,
    outline = c.borderStrong, outlineVariant = c.border, inverseSurface = c.fg, inverseOnSurface = c.bg, inversePrimary = c.primaryFg, scrim = c.scrim,
) else lightColorScheme(
    primary = c.primary, onPrimary = c.primaryFg, primaryContainer = c.primary.copy(alpha = 0.15f), onPrimaryContainer = c.primary,
    secondary = c.mutedFg, onSecondary = c.bg, secondaryContainer = c.muted, onSecondaryContainer = c.fg,
    tertiary = Accents.violet.c600, onTertiary = Color.White, tertiaryContainer = Accents.violet.soft, onTertiaryContainer = Accents.violet.c600,
    error = c.danger, onError = Color.White, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
    background = c.bg, onBackground = c.fg, surface = c.card, onSurface = c.fg, surfaceVariant = c.muted, onSurfaceVariant = c.mutedFg,
    surfaceContainer = c.card, surfaceContainerLow = c.card, surfaceContainerLowest = c.card, surfaceContainerHigh = c.muted, surfaceContainerHighest = c.border,
    outline = c.borderStrong, outlineVariant = c.border, inverseSurface = c.fg, inverseOnSurface = c.bg, inversePrimary = c.primaryFg, scrim = c.scrim,
)

/** rounded-[20px] cards · rounded-[14px] panels · rounded-[8px] tiles */
val PersoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun PersoraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) BentoDark else BentoLight
    // Equal palettes are a no-op write (structural equality), so this never invalidates composition needlessly.
    Bento.palette = colors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = schemeFor(colors), typography = PersoraTypography, shapes = PersoraShapes, content = content)
}
