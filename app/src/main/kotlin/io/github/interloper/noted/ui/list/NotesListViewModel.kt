package io.github.interloper.noted.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.interloper.noted.data.Label
import io.github.interloper.noted.data.NoteWithLabels
import io.github.interloper.noted.data.NotesRepository
import io.github.interloper.noted.domain.NoteFilter
import io.github.interloper.noted.domain.visibleNotes
import io.github.interloper.noted.security.LockState
import io.github.interloper.noted.security.PinHasher
import io.github.interloper.noted.settings.Settings
import io.github.interloper.noted.settings.SettingsRepository
import io.github.interloper.noted.settings.ViewMode
import io.github.interloper.noted.ui.common.notedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesListUiState(
    val notes: List<NoteWithLabels> = emptyList(),
    val labels: List<Label> = emptyList(),
    /** The filter actually applied; falls back to All if the chosen label was deleted or is locked. */
    val filter: NoteFilter = NoteFilter.All,
    val query: String = "",
    /** Ids of selected notes that are currently visible. */
    val selection: Set<Long> = emptySet(),
    val settings: Settings = Settings(),
    val unlocked: Boolean = false,
) {
    val inBin: Boolean get() = filter == NoteFilter.Bin
    val selectedNotes: List<NoteWithLabels> get() = notes.filter { it.note.id in selection }
}

class NotesListViewModel(
    private val repository: NotesRepository,
    private val settingsRepository: SettingsRepository,
    private val lockState: LockState,
) : ViewModel() {

    private val chosenFilter = MutableStateFlow<NoteFilter>(NoteFilter.All)
    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    private data class Data(
        val active: List<NoteWithLabels>,
        val binned: List<NoteWithLabels>,
        val labels: List<Label>,
        val settings: Settings,
        val unlocked: Boolean,
    )

    private val data = combine(
        repository.observeActiveNotes(),
        repository.observeBin(),
        repository.observeLabels(),
        settingsRepository.settings,
        lockState.unlocked,
        ::Data,
    )

    /** Null until the first load finishes, so the screen can avoid flashing its empty state. */
    val uiState: StateFlow<NotesListUiState?> =
        combine(data, chosenFilter, query, selection) { d, chosen, q, selected ->
            val filter = effectiveFilter(chosen, d.labels, d.unlocked)
            val notes = visibleNotes(d.active, d.binned, filter, q, d.unlocked)
            val visibleIds = notes.mapTo(HashSet()) { it.note.id }
            NotesListUiState(
                notes = notes,
                labels = d.labels,
                filter = filter,
                query = q,
                selection = selected.filterTo(HashSet()) { it in visibleIds },
                settings = d.settings,
                unlocked = d.unlocked,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            val last = s.lastLabelId
            if (s.rememberLastLabel && last != null && chosenFilter.value == NoteFilter.All) {
                chosenFilter.value = NoteFilter.ByLabel(last)
            }
        }
    }

    private fun effectiveFilter(chosen: NoteFilter, labels: List<Label>, unlocked: Boolean): NoteFilter {
        if (chosen !is NoteFilter.ByLabel) return chosen
        val label = labels.firstOrNull { it.id == chosen.labelId } ?: return NoteFilter.All
        return if (label.locked && !unlocked) NoteFilter.All else chosen
    }

    /** Callers must unlock first when [filter] is a locked label; otherwise it shows as All. */
    fun selectFilter(filter: NoteFilter) {
        chosenFilter.value = filter
        selection.value = emptySet()
        viewModelScope.launch {
            settingsRepository.setLastLabelId((filter as? NoteFilter.ByLabel)?.labelId)
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleSelected(noteId: Long) = selection.update { if (noteId in it) it - noteId else it + noteId }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun selectAll() {
        selection.value = uiState.value?.notes.orEmpty().mapTo(HashSet()) { it.note.id }
    }

    /** Pins the selection, or unpins it when every selected note is already pinned. */
    fun togglePinSelected() {
        val selected = uiState.value?.selectedNotes.orEmpty()
        if (selected.isEmpty()) return
        val pin = selected.any { !it.note.pinned }
        val ids = selected.map { it.note.id }
        clearSelection()
        viewModelScope.launch { repository.setPinned(ids, pin) }
    }

    /** Locks or unlocks the selected notes. Unlocking must be preceded by the PIN check; the screen does that. */
    fun setLockedSelected(locked: Boolean) {
        val ids = uiState.value?.selectedNotes.orEmpty().map { it.note.id }
        clearSelection()
        viewModelScope.launch { repository.setLocked(ids, locked) }
    }

    suspend fun setPin(pin: PinHasher.Hashed) = settingsRepository.setPin(pin.hash, pin.salt)

    /** Moves the selection to the bin and returns the ids so the move can be undone. */
    fun binSelected(): List<Long> {
        val ids = uiState.value?.selectedNotes.orEmpty().map { it.note.id }
        clearSelection()
        viewModelScope.launch { repository.moveToBin(ids) }
        return ids
    }

    fun restore(ids: Collection<Long>) {
        viewModelScope.launch { repository.restoreFromBin(ids) }
    }

    fun restoreSelected() {
        val ids = uiState.value?.selectedNotes.orEmpty().map { it.note.id }
        clearSelection()
        restore(ids)
    }

    fun deleteSelectedForever() {
        val ids = uiState.value?.selectedNotes.orEmpty().map { it.note.id }
        clearSelection()
        viewModelScope.launch { repository.deletePermanently(ids) }
    }

    /** Deletes the binned notes currently shown; locked notes hidden from the bin are left alone. */
    fun emptyBin() {
        val state = uiState.value ?: return
        if (!state.inBin) return
        val ids = state.notes.map { it.note.id }
        clearSelection()
        viewModelScope.launch { repository.deletePermanently(ids) }
    }

    fun toggleViewMode() {
        val current = uiState.value?.settings?.viewMode ?: return
        viewModelScope.launch {
            settingsRepository.setViewMode(if (current == ViewMode.Grid) ViewMode.List else ViewMode.Grid)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { NotesListViewModel(notedApp.repository, notedApp.settings, notedApp.lockState) }
        }
    }
}
