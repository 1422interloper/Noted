package io.github.interloper.noted

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.interloper.noted.backup.AutoBackup
import io.github.interloper.noted.backup.BackupFiles
import io.github.interloper.noted.backup.BackupManager
import io.github.interloper.noted.data.AppDatabase
import io.github.interloper.noted.data.NotesRepository
import io.github.interloper.noted.security.LockState
import io.github.interloper.noted.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Holds the app-wide singletons; screens reach them through their ViewModel factories. */
class NotedApp : Application() {

    /** Outlives any screen; used for writes that must finish after their ViewModel is gone. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: NotesRepository by lazy { NotesRepository(database) }
    val backupManager: BackupManager by lazy { BackupManager(database) }
    val settings: SettingsRepository by lazy { SettingsRepository(settingsStore) }
    val lockState = LockState()

    override fun onCreate() {
        super.onCreate()

        // Auto-clear bin: runs at launch and again whenever the retention setting changes.
        appScope.launch {
            settings.settings.map { it.binRetention }.distinctUntilChanged().collect { retention ->
                retention.millis?.let { repository.purgeBinOlderThan(it) }
            }
        }

        AutoBackup(
            target = settings.settings.map { if (it.autoBackup) it.backupFolderUri else null },
            changes = combine(repository.observeActiveNotes(), repository.observeBin(), repository.observeLabels()) { _, _, _ -> },
            export = backupManager::export,
            write = { folder, text -> BackupFiles.writeToFolder(this, folder, text) },
            onError = { Log.w("Noted", "Auto-backup failed", it) },
        ).start(appScope)
    }
}
