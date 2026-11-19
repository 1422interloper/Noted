package io.github.interloper.noted.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[THEME_MODE].toEnum(d.themeMode),
            amoled = p[AMOLED] ?: d.amoled,
            dynamicColor = p[DYNAMIC_COLOR] ?: d.dynamicColor,
            seedColor = p[SEED_COLOR] ?: d.seedColor,
            font = p[FONT].toEnum(d.font),
            viewMode = p[VIEW_MODE].toEnum(d.viewMode),
            titleOnly = p[TITLE_ONLY] ?: d.titleOnly,
            previewLines = (p[PREVIEW_LINES] ?: d.previewLines).coerceIn(Settings.PREVIEW_LINES_RANGE),
            stackedLabels = p[STACKED_LABELS] ?: d.stackedLabels,
            rememberLastLabel = p[REMEMBER_LAST_LABEL] ?: d.rememberLastLabel,
            lastLabelId = p[LAST_LABEL_ID],
            binRetention = p[BIN_RETENTION].toEnum(d.binRetention),
            biometricUnlock = p[BIOMETRIC_UNLOCK] ?: d.biometricUnlock,
            autoBackup = p[AUTO_BACKUP] ?: d.autoBackup,
            backupFolderUri = p[BACKUP_FOLDER_URI],
            pinHash = p[PIN_HASH],
            pinSalt = p[PIN_SALT],
        )
    }

    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[THEME_MODE] = value.name }
    suspend fun setAmoled(value: Boolean) = store.edit { it[AMOLED] = value }
    suspend fun setDynamicColor(value: Boolean) = store.edit { it[DYNAMIC_COLOR] = value }
    suspend fun setSeedColor(value: Int) = store.edit { it[SEED_COLOR] = value }
    suspend fun setFont(value: AppFont) = store.edit { it[FONT] = value.name }
    suspend fun setViewMode(value: ViewMode) = store.edit { it[VIEW_MODE] = value.name }
    suspend fun setTitleOnly(value: Boolean) = store.edit { it[TITLE_ONLY] = value }
    suspend fun setPreviewLines(value: Int) =
        store.edit { it[PREVIEW_LINES] = value.coerceIn(Settings.PREVIEW_LINES_RANGE) }
    suspend fun setStackedLabels(value: Boolean) = store.edit { it[STACKED_LABELS] = value }
    suspend fun setRememberLastLabel(value: Boolean) = store.edit { it[REMEMBER_LAST_LABEL] = value }
    suspend fun setBinRetention(value: BinRetention) = store.edit { it[BIN_RETENTION] = value.name }
    suspend fun setBiometricUnlock(value: Boolean) = store.edit { it[BIOMETRIC_UNLOCK] = value }
    suspend fun setAutoBackup(value: Boolean) = store.edit { it[AUTO_BACKUP] = value }

    suspend fun setLastLabelId(value: Long?) = store.edit {
        if (value == null) it.remove(LAST_LABEL_ID) else it[LAST_LABEL_ID] = value
    }

    /** Clearing the folder also turns auto-backup off, since there is nowhere to write to. */
    suspend fun setBackupFolderUri(value: String?) = store.edit {
        if (value == null) {
            it.remove(BACKUP_FOLDER_URI)
            it[AUTO_BACKUP] = false
        } else {
            it[BACKUP_FOLDER_URI] = value
        }
    }

    suspend fun setPin(hash: String, salt: String) = store.edit {
        it[PIN_HASH] = hash
        it[PIN_SALT] = salt
    }

    /** Forgets the PIN; fingerprint unlock goes with it, since it only stands in for the PIN. */
    suspend fun clearPin() = store.edit {
        it.remove(PIN_HASH)
        it.remove(PIN_SALT)
        it[BIOMETRIC_UNLOCK] = false
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED = booleanPreferencesKey("amoled")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SEED_COLOR = intPreferencesKey("seed_color")
        val FONT = stringPreferencesKey("font")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val TITLE_ONLY = booleanPreferencesKey("title_only")
        val PREVIEW_LINES = intPreferencesKey("preview_lines")
        val STACKED_LABELS = booleanPreferencesKey("stacked_labels")
        val REMEMBER_LAST_LABEL = booleanPreferencesKey("remember_last_label")
        val LAST_LABEL_ID = longPreferencesKey("last_label_id")
        val BIN_RETENTION = stringPreferencesKey("bin_retention")
        val BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
    }
}
