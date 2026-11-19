package io.github.interloper.noted.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import io.github.interloper.noted.settings.AppFont
import io.github.interloper.noted.settings.Settings
import io.github.interloper.noted.settings.ThemeMode

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
@ReadOnlyComposable
fun Settings.isDark(): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun NotedTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = settings.isDark()
    val context = LocalContext.current
    val base = if (settings.dynamicColor && supportsDynamicColor) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(settings.seedColor, dark) { seedColorScheme(settings.seedColor, dark) }
    }
    val scheme = if (dark && settings.amoled) base.amoled() else base
    val typography = remember(settings.font) { typographyFor(settings.font) }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/** Pure black behind content; raised surfaces keep their tint so cards and sheets stay distinguishable. */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)

private fun typographyFor(font: AppFont): Typography {
    val family = when (font) {
        AppFont.System -> return Typography()
        AppFont.Serif -> FontFamily.Serif
        AppFont.Monospace -> FontFamily.Monospace
    }
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = d.headlineLarge.copy(fontFamily = family),
        headlineMedium = d.headlineMedium.copy(fontFamily = family),
        headlineSmall = d.headlineSmall.copy(fontFamily = family),
        titleLarge = d.titleLarge.copy(fontFamily = family),
        titleMedium = d.titleMedium.copy(fontFamily = family),
        titleSmall = d.titleSmall.copy(fontFamily = family),
        bodyLarge = d.bodyLarge.copy(fontFamily = family),
        bodyMedium = d.bodyMedium.copy(fontFamily = family),
        bodySmall = d.bodySmall.copy(fontFamily = family),
        labelLarge = d.labelLarge.copy(fontFamily = family),
        labelMedium = d.labelMedium.copy(fontFamily = family),
        labelSmall = d.labelSmall.copy(fontFamily = family),
    )
}
