package io.github.interloper.noted.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.interloper.noted.data.Label
import io.github.interloper.noted.data.NoteWithLabels
import io.github.interloper.noted.data.NotesRepository
import io.github.interloper.noted.security.PinHasher
import io.github.interloper.noted.ui.common.notedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EditorLoad {
    data object Loading : EditorLoad
    /** The note to edit; for a new note everything is empty. A [binned] note is shown read-only. */
    data class Ready(val title: String, val content: String, val binned: Boolean = false) : EditorLoad
    /** The note no longer exists (deleted elsewhere). */
    data object Missing : EditorLoad
}

/**
 * Autosaves one note. A new note gets its database row on the first non-blank save; a note left with no title and no
 * text is deleted when the editor closes. Until that first save, the pin and labels chosen for a new note are held
 * here and applied when the row is created.
 *
 * @param initialNoteId the note to open, or null for a new note.
 * @param initialLabelId label a new note starts with (the label that was selected in the list), if any.
 * @param appScope used for the final save so it completes after this ViewModel is cleared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModel(
    private val repository: NotesRepository,
    private val appScope: CoroutineScope,
    initialNoteId: Long?,
    initialLabelId: Long?,
    private val saveDelayMillis: Long = SAVE_DELAY_MILLIS,
    private val savePin: suspend (PinHasher.Hashed) -> Unit = {},
) : ViewModel() {

    private val _noteId = MutableStateFlow(initialNoteId)
    /** Null until a new note has been saved for the first time. */
    val noteId: StateFlow<Long?> = _noteId.asStateFlow()

    private val _load = MutableStateFlow<EditorLoad>(EditorLoad.Loading)
    val load: StateFlow<EditorLoad> = _load.asStateFlow()

    /** Live pin, bin and label state of the saved note. */
    val note: StateFlow<NoteWithLabels?> = _noteId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeNote(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val labels: StateFlow<List<Label>> =
        repository.observeLabels().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Pin and labels of a note that has no database row yet.
    private val pendingPinned = MutableStateFlow(false)
    private val pendingLabelIds = MutableStateFlow(setOfNotNull(initialLabelId))
    private val pendingLocked = MutableStateFlow(false)

    val locked: StateFlow<Boolean> = combine(note, pendingLocked) { saved, pending -> saved?.note?.locked ?: pending }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pinned: StateFlow<Boolean> = combine(note, pendingPinned) { saved, pending -> saved?.note?.pinned ?: pending }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val labelIds: StateFlow<Set<Long>> =
        combine(note, pendingLabelIds) { saved, pending -> saved?.labels?.mapTo(HashSet()) { it.id } ?: pending }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), setOfNotNull(initialLabelId))

    var title by mutableStateOf("")
        private set

    /** True once the screen has put the loaded note into its text fields; edits before that are ignored. */
    var editorInitialised = false
        private set

    /**
     * The body as last reported by the editor. A recreated screen (rotation, theme change) refills its editor from
     * this rather than from saved instance state, which a long note could overflow.
     */
    var bodyHtml = ""
        private set

    private var plainText = ""
    // Edits are counted rather than flagged: text typed while a save is being written must still count as unsaved
    // when that save finishes. All of this state is only touched on the main thread.
    private var revision = 0
    private var savedRevision = 0
    private var readOnly = false
    private var deleted = false
    private var saveJob: Job? = null
    private val saveLock = Mutex()

    init {
        viewModelScope.launch {
            if (initialNoteId == null) {
                _load.value = EditorLoad.Ready("", "")
            } else {
                val existing = repository.getNote(initialNoteId)
                if (existing == null) {
                    _load.value = EditorLoad.Missing
                } else {
                    readOnly = existing.note.isBinned
                    title = existing.note.title
                    _load.value = EditorLoad.Ready(existing.note.title, existing.note.content, existing.note.isBinned)
                }
            }
        }
    }

    /**
     * Records what the editor shows right after loading. The rich-text editor may normalise stored HTML, so the
     * baseline is its own rendering; opening a note without editing it then never counts as a change.
     */
    fun onEditorInitialised(html: String, plainText: String) {
        bodyHtml = html
        this.plainText = plainText
        editorInitialised = true
    }

    fun onTitleChanged(value: String) {
        if (value == title) return
        title = value
        markDirty()
    }

    fun onBodyChanged(html: String, plainText: String) {
        if (!editorInitialised || html == bodyHtml) return
        bodyHtml = html
        this.plainText = plainText
        markDirty()
    }

    private fun markDirty() {
        if (readOnly) return
        revision++
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(saveDelayMillis)
            persist()
        }
    }

    private val isBlank: Boolean get() = title.isBlank() && plainText.isBlank()

    /**
     * Writes the current text if it has changed since the last save. Not cancellable: a newer edit cancels the pending
     * save job, and a write abandoned halfway would lose a new note's id, so the next save would create it again.
     */
    private suspend fun persist() = withContext(NonCancellable) {
        saveLock.withLock {
            val saving = revision
            if (saving == savedRevision || readOnly || deleted) return@withLock
            val title = title.trim()
            val html = bodyHtml
            val text = plainText
            val id = _noteId.value
            if (id == null) {
                if (title.isEmpty() && text.isBlank()) return@withLock
                val newId = repository.createNote(title, html, text)
                if (pendingPinned.value) repository.setPinned(listOf(newId), true)
                if (pendingLocked.value) repository.setLocked(listOf(newId), true)
                // A chosen label may have been deleted while the editor was open; the note is kept either way.
                val existing = repository.observeLabels().first().mapTo(HashSet()) { it.id }
                val labelIds = pendingLabelIds.value.filter { it in existing }
                if (labelIds.isNotEmpty()) runCatching { repository.setLabelsForNote(newId, labelIds) }
                _noteId.value = newId
            } else {
                repository.saveNoteText(id, title, html, text)
            }
            savedRevision = saving
        }
    }

    /** Saves pending edits now; called when the app goes to the background. */
    fun flush() {
        saveJob?.cancel()
        appScope.launch(Dispatchers.Main.immediate) { persist() }
    }

    /** Final save when the editor closes; removes the note if it ended up empty. */
    private fun close() {
        saveJob?.cancel()
        appScope.launch(Dispatchers.Main.immediate) {
            persist()
            val id = _noteId.value
            if (id != null && isBlank && editorInitialised && !readOnly && !deleted) {
                repository.deletePermanently(listOf(id))
            }
        }
    }

    override fun onCleared() = close()

    fun togglePin() {
        if (_noteId.value == null) {
            pendingPinned.update { !it }
            return
        }
        val current = note.value ?: return
        viewModelScope.launch { repository.setPinned(listOf(current.note.id), !current.note.pinned) }
    }

    /** Locks or unlocks the note. Unlocking must be preceded by the PIN check; the screen does that. */
    fun setLocked(locked: Boolean) {
        val id = _noteId.value
        if (id == null) {
            pendingLocked.value = locked
            return
        }
        viewModelScope.launch { repository.setLocked(listOf(id), locked) }
    }

    /** Stores a newly chosen PIN, then locks the note with it. */
    fun setPinAndLock(pin: PinHasher.Hashed) {
        viewModelScope.launch {
            savePin(pin)
            setLocked(true)
        }
    }

    fun setLabels(labelIds: Set<Long>) {
        val id = _noteId.value
        if (id == null) {
            pendingLabelIds.value = labelIds
            return
        }
        viewModelScope.launch { repository.setLabelsForNote(id, labelIds) }
    }

    /**
     * Saves, then moves the note to the bin. [onDone] runs when it is safe to leave the screen, with the id of the
     * binned note, or null if there was nothing to bin (a new note that was still empty).
     */
    fun moveToBin(onDone: (binnedId: Long?) -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            persist()
            val id = _noteId.value
            if (id != null) repository.moveToBin(listOf(id))
            readOnly = true
            onDone(id)
        }
    }

    fun restore(onDone: () -> Unit) {
        val id = _noteId.value ?: return
        viewModelScope.launch {
            repository.restoreFromBin(listOf(id))
            onDone()
        }
    }

    fun deleteForever(onDone: () -> Unit) {
        val id = _noteId.value ?: return
        deleted = true
        viewModelScope.launch {
            repository.deletePermanently(listOf(id))
            onDone()
        }
    }

    companion object {
        const val SAVE_DELAY_MILLIS = 300L

        fun factory(noteId: Long?, labelId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val settings = notedApp.settings
                EditorViewModel(
                    repository = notedApp.repository,
                    appScope = notedApp.appScope,
                    initialNoteId = noteId,
                    initialLabelId = labelId,
                    savePin = { settings.setPin(it.hash, it.salt) },
                )
            }
        }
    }
}
