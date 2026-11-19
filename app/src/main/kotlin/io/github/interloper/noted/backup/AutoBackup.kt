package io.github.interloper.noted.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Rewrites the backup file in the chosen folder shortly after notes or labels change.
 *
 * @param target emits the folder to write to, or null while auto-backup is off or no folder is chosen.
 * @param changes emits whenever stored notes or labels change (and once on start).
 * @param export produces the backup text.
 * @param write stores the text in the folder; failures (folder removed, permission revoked) are reported to [onError].
 */
class AutoBackup(
    private val target: Flow<String?>,
    private val changes: Flow<Any?>,
    private val export: suspend () -> String,
    private val write: suspend (folderUri: String, text: String) -> Unit,
    private val onError: (Exception) -> Unit = {},
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(target.distinctUntilChanged(), changes) { folder, _ -> folder }
            .debounce(debounceMillis)
            .collectLatest { folder ->
                if (folder == null) return@collectLatest
                try {
                    write(folder, export())
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(e)
                }
            }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 3000L
        const val FILE_NAME = "noted-backup.json"
    }
}
