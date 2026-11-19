package io.github.interloper.noted.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

/** Seed colours offered in settings when wallpaper colours are off or unavailable. */
val SeedColors: List<Int> = listOf(
    0xFF6750A4, 0xFF3F51B5, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFC0CA33,
    0xFFFFB300, 0xFFF4511E, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41,
).map { it.toInt() }

/** Builds a full Material 3 colour scheme from one seed colour, the same way Material You does from a wallpaper. */
fun seedColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val s = SchemeTonalSpot(listOf(Hct.fromInt(seed)), dark, 0.0)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(s.primary),
        onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer),
        onPrimaryContainer = Color(s.onPrimaryContainer),
        inversePrimary = Color(s.inversePrimary),
        secondary = Color(s.secondary),
        onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer),
        onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary),
        onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer),
        onTertiaryContainer = Color(s.onTertiaryContainer),
        background = Color(s.background),
        onBackground = Color(s.onBackground),
        surface = Color(s.surface),
        onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant),
        onSurfaceVariant = Color(s.onSurfaceVariant),
        surfaceTint = Color(s.surfaceTint),
        inverseSurface = Color(s.inverseSurface),
        inverseOnSurface = Color(s.inverseOnSurface),
        error = Color(s.error),
        onError = Color(s.onError),
        errorContainer = Color(s.errorContainer),
        onErrorContainer = Color(s.onErrorContainer),
        outline = Color(s.outline),
        outlineVariant = Color(s.outlineVariant),
        scrim = Color(s.scrim),
        surfaceBright = Color(s.surfaceBright),
        surfaceDim = Color(s.surfaceDim),
        surfaceContainer = Color(s.surfaceContainer),
        surfaceContainerHigh = Color(s.surfaceContainerHigh),
        surfaceContainerHighest = Color(s.surfaceContainerHighest),
        surfaceContainerLow = Color(s.surfaceContainerLow),
        surfaceContainerLowest = Color(s.surfaceContainerLowest),
    )
}
