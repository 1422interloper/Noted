package io.github.interloper.noted.settings

enum class ThemeMode { System, Light, Dark }

enum class ViewMode { Grid, List }

enum class AppFont { System, Serif, Monospace }

/** How long notes stay in the bin before they are deleted for good. */
enum class BinRetention(val days: Int?) {
    Never(null), OneDay(1), OneWeek(7), TwoWeeks(14), OneMonth(30);

    val millis: Long? get() = days?.let { it * 24L * 60 * 60 * 1000 }
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Pure black backgrounds in dark theme. */
    val amoled: Boolean = false,
    /** Use the wallpaper palette (Android 12+); otherwise the palette is generated from [seedColor]. */
    val dynamicColor: Boolean = true,
    val seedColor: Int = DEFAULT_SEED_COLOR,
    val font: AppFont = AppFont.System,
    val viewMode: ViewMode = ViewMode.Grid,
    val titleOnly: Boolean = false,
    val previewLines: Int = DEFAULT_PREVIEW_LINES,
    /** Wrap the label chips onto several rows instead of one scrolling row. */
    val stackedLabels: Boolean = false,
    val rememberLastLabel: Boolean = false,
    val lastLabelId: Long? = null,
    val binRetention: BinRetention = BinRetention.Never,
    val biometricUnlock: Boolean = false,
    val autoBackup: Boolean = false,
    val backupFolderUri: String? = null,
    /** PBKDF2 hash and salt of the locked-labels PIN, Base64; null until a PIN is set. */
    val pinHash: String? = null,
    val pinSalt: String? = null,
) {
    val hasPin: Boolean get() = pinHash != null && pinSalt != null

    companion object {
        const val DEFAULT_SEED_COLOR: Int = 0xFF6750A4.toInt()
        const val DEFAULT_PREVIEW_LINES = 3
        val PREVIEW_LINES_RANGE = 1..10
    }
}
