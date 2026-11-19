package io.github.interloper.noted.ui.labels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.interloper.noted.data.Label
import io.github.interloper.noted.data.NotesRepository
import io.github.interloper.noted.security.PinHasher
import io.github.interloper.noted.settings.SettingsRepository
import io.github.interloper.noted.ui.common.notedApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LabelKind { Plain, Folder, Locked }

val Label.kind: LabelKind
    get() = when {
        locked -> LabelKind.Locked
        folder -> LabelKind.Folder
        else -> LabelKind.Plain
    }

class LabelsViewModel(
    private val repository: NotesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Null until the first load, so the screen does not flash its empty state. */
    val labels: StateFlow<List<Label>?> = repository.observeLabels()
        .map<List<Label>, List<Label>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Returns false if the name is already taken. */
    suspend fun create(name: String, kind: LabelKind): Boolean =
        repository.createLabel(name.trim(), locked = kind == LabelKind.Locked, folder = kind == LabelKind.Folder) != null

    /** Returns false if another label already has the name. */
    suspend fun rename(id: Long, name: String): Boolean = repository.renameLabel(id, name.trim())

    suspend fun setPin(pin: PinHasher.Hashed) = settingsRepository.setPin(pin.hash, pin.salt)

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteLabel(id) }
    }

    fun deleteAllUnlocked() {
        viewModelScope.launch { repository.deleteAllUnlockedLabels() }
    }

    /** Stores the order left by a drag. */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderLabels(orderedIds) }
    }

    /** Moves a label one place up ([offset] -1) or down (+1); the non-drag way to reorder. */
    fun move(id: Long, offset: Int) {
        val ids = labels.value.orEmpty().map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + offset
        if (from < 0 || to !in ids.indices) return
        ids[from] = ids[to]
        ids[to] = id
        viewModelScope.launch { repository.reorderLabels(ids) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LabelsViewModel(notedApp.repository, notedApp.settings) }
        }
    }
}
