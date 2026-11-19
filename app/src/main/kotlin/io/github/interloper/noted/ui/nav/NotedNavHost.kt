package io.github.interloper.noted.ui.nav

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.interloper.noted.security.LockState
import io.github.interloper.noted.settings.Settings
import io.github.interloper.noted.ui.editor.EditorScreen
import io.github.interloper.noted.ui.editor.EditorViewModel
import io.github.interloper.noted.ui.labels.LabelsScreen
import io.github.interloper.noted.ui.list.NotesListScreen
import io.github.interloper.noted.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object NotesRoute : NavKey

/**
 * @param noteId the note to open, or null for a new note.
 * @param labelId label for a new note: the one selected in the list when it was created.
 */
@Serializable
data class EditorRoute(val noteId: Long?, val labelId: Long? = null) : NavKey

@Serializable
data object LabelsRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Composable
fun NotedNavHost(settings: Settings, lockState: LockState) {
    val backStack = rememberNavBackStack(NotesRoute)
    // Handed from the editor to the list so the list can show "Moved to bin · Undo".
    var binnedInEditor by remember { mutableStateOf<Long?>(null) }

    // Screens can ask to close more than once (a finished action and a back press); only the top entry is popped,
    // and the list always stays.
    fun close(key: NavKey) {
        if (backStack.size > 1 && backStack.last() == key) backStack.removeAt(backStack.lastIndex)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // Gives each entry its own ViewModels, cleared when the entry leaves the back stack. The editor relies on
            // that for its final save.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<NotesRoute> {
                NotesListScreen(
                    lockState = lockState,
                    onOpenNote = { backStack.add(EditorRoute(noteId = it)) },
                    onNewNote = { backStack.add(EditorRoute(noteId = null, labelId = it)) },
                    onOpenLabels = { backStack.add(LabelsRoute) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                    binnedInEditor = binnedInEditor,
                    onBinnedInEditorShown = { binnedInEditor = null },
                )
            }
            entry<EditorRoute> { key ->
                EditorEntry(
                    key = key,
                    settings = settings,
                    lockState = lockState,
                    onBack = { close(key) },
                    onBinned = { noteId ->
                        binnedInEditor = noteId
                        close(key)
                    },
                )
            }
            entry<LabelsRoute> { key ->
                LabelsScreen(settings, lockState, onBack = { close(key) })
            }
            entry<SettingsRoute> { key ->
                SettingsScreen(settings, lockState, onBack = { close(key) })
            }
        },
    )
}

@Composable
private fun EditorEntry(
    key: EditorRoute,
    settings: Settings,
    lockState: LockState,
    onBack: () -> Unit,
    onBinned: (noteId: Long) -> Unit,
) {
    // A new note has no id in its route. Once it is saved the id is kept here, so the editor reopens that note
    // rather than starting another blank one if the process is killed and restored.
    var noteId by rememberSaveable { mutableStateOf(key.noteId) }
    val viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(noteId, key.labelId))
    LaunchedEffect(viewModel) {
        viewModel.noteId.collect { if (it != null) noteId = it }
    }

    // A locked note closes when the app locks, which happens on leaving the foreground. A note the user has just
    // locked from the editor stays open until then, so locking it does not throw them out of it.
    var lockedHere by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) lockedHere = false
    }
    val unlocked by lockState.unlocked.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    if (note?.isProtected == true && !unlocked && !lockedHere) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    EditorScreen(
        noteId = key.noteId,
        newNoteLabelId = key.labelId,
        settings = settings,
        lockState = lockState,
        onBack = onBack,
        onBinned = onBinned,
        onLocked = { lockedHere = true },
        viewModel = viewModel,
    )
}
