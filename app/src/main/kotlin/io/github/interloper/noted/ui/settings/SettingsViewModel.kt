package io.github.interloper.noted.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.interloper.noted.backup.BackupFiles
import io.github.interloper.noted.backup.BackupFormatException
import io.github.interloper.noted.backup.BackupManager
import io.github.interloper.noted.backup.RestoreResult
import io.github.interloper.noted.data.NotesRepository
import io.github.interloper.noted.settings.SettingsRepository
import io.github.interloper.noted.ui.common.notedApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Outcome of a backup action, shown as a snackbar. */
sealed interface BackupMessage {
    data object Exported : BackupMessage
    data object ExportFailed : BackupMessage
    data class Restored(val result: RestoreResult) : BackupMessage
    /** The file could be read but is not a Noted backup (or is from a newer version). */
    data object NotABackup : BackupMessage
    data object RestoreFailed : BackupMessage
}

class SettingsViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val notesRepository: NotesRepository,
) : ViewModel() {

    private val _messages = Channel<BackupMessage>(Channel.BUFFERED)
    val messages: Flow<BackupMessage> = _messages.receiveAsFlow()

    /** Applies one settings change, e.g. `update { setAmoled(true) }`. */
    fun update(change: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.change() }
    }

    /**
     * Removes the PIN. Note locks are removed and locked labels become ordinary labels first, so nothing is left
     * sealed with no way to open it.
     */
    fun removePin() {
        viewModelScope.launch {
            notesRepository.removeAllLocks()
            settingsRepository.clearPin()
        }
    }

    fun export(target: Uri) {
        viewModelScope.launch {
            val message = try {
                BackupFiles.write(appContext, target, backupManager.export())
                BackupMessage.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackupMessage.ExportFailed
            }
            _messages.send(message)
        }
    }

    fun restore(source: Uri) {
        viewModelScope.launch {
            val message = try {
                BackupMessage.Restored(backupManager.restore(BackupFiles.read(appContext, source)))
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupFormatException) {
                BackupMessage.NotABackup
            } catch (_: Exception) {
                BackupMessage.RestoreFailed
            }
            _messages.send(message)
        }
    }

    /** Keeps access to the picked folder across restarts and turns auto-backup on. */
    fun setBackupFolder(folder: Uri) {
        viewModelScope.launch {
            val previous = settingsRepository.settings.first().backupFolderUri
            try {
                appContext.contentResolver.takePersistableUriPermission(folder, FOLDER_FLAGS)
            } catch (_: SecurityException) {
                _messages.send(BackupMessage.ExportFailed)
                return@launch
            }
            settingsRepository.setBackupFolderUri(folder.toString())
            settingsRepository.setAutoBackup(true)
            if (previous != null && previous != folder.toString()) releaseFolder(previous)
        }
    }

    /** Forgets the folder, which also turns auto-backup off. The backup file in it is left alone. */
    fun clearBackupFolder() {
        viewModelScope.launch {
            val previous = settingsRepository.settings.first().backupFolderUri ?: return@launch
            settingsRepository.setBackupFolderUri(null)
            releaseFolder(previous)
        }
    }

    private fun releaseFolder(uri: String) {
        try {
            appContext.contentResolver.releasePersistableUriPermission(Uri.parse(uri), FOLDER_FLAGS)
        } catch (_: SecurityException) {
            // Already revoked from system settings; nothing left to release.
        }
    }

    companion object {
        private const val FOLDER_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(notedApp, notedApp.settings, notedApp.backupManager, notedApp.repository) }
        }
    }
}
