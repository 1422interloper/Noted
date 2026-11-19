package io.github.interloper.noted.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.interloper.noted.R
import io.github.interloper.noted.data.Label
import io.github.interloper.noted.data.NoteWithLabels
import io.github.interloper.noted.domain.NoteFilter
import io.github.interloper.noted.security.LockState
import io.github.interloper.noted.settings.Settings
import io.github.interloper.noted.settings.ViewMode
import io.github.interloper.noted.ui.common.ConfirmDialog
import io.github.interloper.noted.ui.lock.PinSetupDialog
import io.github.interloper.noted.ui.lock.rememberUnlockRequester
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    lockState: LockState,
    onOpenNote: (noteId: Long) -> Unit,
    onNewNote: (labelId: Long?) -> Unit,
    onOpenLabels: () -> Unit,
    onOpenSettings: () -> Unit,
    /** A note that was just moved to the bin from the editor; the list offers to undo it. */
    binnedInEditor: Long? = null,
    onBinnedInEditorShown: () -> Unit = {},
    viewModel: NotesListViewModel = viewModel(factory = NotesListViewModel.Factory),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteForever by remember { mutableStateOf(false) }
    var confirmEmptyBin by remember { mutableStateOf(false) }
    val requestUnlock = rememberUnlockRequester(state?.settings ?: Settings(), lockState)
    // Set while the PIN is being chosen for the first time; runs once it has been saved.
    var afterPinSetup by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** Runs [action] once locked notes may be shown, first asking for a new PIN if none has been set yet. */
    fun whenUnlocked(action: () -> Unit) {
        if (state?.settings?.hasPin == false && !lockState.unlocked.value) afterPinSetup = action else requestUnlock(action)
    }

    /** Binning, restoring or deleting a locked note needs the PIN, like opening it. */
    fun guarded(notes: List<NoteWithLabels>, action: () -> Unit) {
        if (notes.any { it.note.locked }) whenUnlocked(action) else action()
    }

    val selecting = state != null && state.selection.isNotEmpty()
    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    BackHandler(enabled = searching && !selecting) {
        searching = false
        viewModel.setQuery("")
    }

    val binnedMessage = stringResource(R.string.moved_to_bin)
    val undoLabel = stringResource(R.string.undo)
    fun offerUndo(ids: Collection<Long>) {
        scope.launch {
            val result = snackbar.showSnackbar(binnedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(ids)
        }
    }
    LaunchedEffect(binnedInEditor) {
        if (binnedInEditor != null) {
            onBinnedInEditorShown()
            offerUndo(listOf(binnedInEditor))
        }
    }

    Scaffold(
        topBar = {
            when {
                state == null -> TopAppBar(title = { Text(stringResource(R.string.app_name)) })
                selecting -> SelectionTopBar(
                    state = state,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onTogglePin = viewModel::togglePinSelected,
                    onLock = {
                        // Locking needs no PIN entry, only that one exists.
                        if (state.settings.hasPin) viewModel.setLockedSelected(true)
                        else afterPinSetup = { viewModel.setLockedSelected(true) }
                    },
                    onUnlock = { whenUnlocked { viewModel.setLockedSelected(false) } },
                    onBin = { guarded(state.selectedNotes) { offerUndo(viewModel.binSelected()) } },
                    onRestore = { guarded(state.selectedNotes) { viewModel.restoreSelected() } },
                    onDeleteForever = { guarded(state.selectedNotes) { confirmDeleteForever = true } },
                )
                searching -> SearchTopBar(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onClose = {
                        searching = false
                        viewModel.setQuery("")
                    },
                )
                else -> MainTopBar(
                    state = state,
                    onSearch = { searching = true },
                    onToggleView = viewModel::toggleViewMode,
                    onOpenLabels = onOpenLabels,
                    onOpenSettings = onOpenSettings,
                    onEmptyBin = { guarded(state.notes) { confirmEmptyBin = true } },
                )
            }
        },
        floatingActionButton = {
            if (state != null && !state.inBin && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = { onNewNote((state.filter as? NoteFilter.ByLabel)?.labelId) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_note)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state == null) return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!searching) {
                LabelChips(
                    labels = state.labels,
                    filter = state.filter,
                    stacked = state.settings.stackedLabels,
                    onSelect = { filter ->
                        val label = (filter as? NoteFilter.ByLabel)?.let { f -> state.labels.firstOrNull { it.id == f.labelId } }
                        if (label != null && label.locked) requestUnlock { viewModel.selectFilter(filter) } else viewModel.selectFilter(filter)
                    },
                )
            }
            if (state.notes.isEmpty()) {
                EmptyState(
                    when {
                        state.query.isNotBlank() -> stringResource(R.string.empty_search)
                        state.inBin -> stringResource(R.string.empty_bin_state)
                        state.filter is NoteFilter.ByLabel -> stringResource(R.string.empty_label)
                        else -> stringResource(R.string.empty_notes)
                    },
                )
            } else {
                NotesGrid(
                    state = state,
                    onClick = { note ->
                        when {
                            selecting -> viewModel.toggleSelected(note.note.id)
                            note.note.locked -> whenUnlocked { onOpenNote(note.note.id) }
                            else -> onOpenNote(note.note.id)
                        }
                    },
                    onLongClick = { note -> viewModel.toggleSelected(note.note.id) },
                )
            }
        }
    }

    afterPinSetup?.let { action ->
        PinSetupDialog(
            title = stringResource(R.string.set_pin),
            onPinChosen = { hashed ->
                scope.launch {
                    viewModel.setPin(hashed)
                    lockState.unlock()
                    afterPinSetup = null
                    action()
                }
            },
            onDismiss = { afterPinSetup = null },
        )
    }
    if (confirmDeleteForever && state != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_forever_title),
            text = pluralStringResource(R.plurals.delete_forever_text, state.selection.size, state.selection.size),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = viewModel::deleteSelectedForever,
            onDismiss = { confirmDeleteForever = false },
        )
    }
    if (confirmEmptyBin) {
        ConfirmDialog(
            title = stringResource(R.string.empty_bin),
            text = stringResource(R.string.empty_bin_text),
            confirmLabel = stringResource(R.string.empty_bin),
            destructive = true,
            onConfirm = viewModel::emptyBin,
            onDismiss = { confirmEmptyBin = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    state: NotesListUiState,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onOpenLabels: () -> Unit,
    onOpenSettings: () -> Unit,
    onEmptyBin: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
            IconButton(onClick = onToggleView) {
                if (state.settings.viewMode == ViewMode.Grid) {
                    Icon(Icons.Default.ViewAgenda, stringResource(R.string.view_as_list))
                } else {
                    Icon(Icons.Default.GridView, stringResource(R.string.view_as_grid))
                }
            }
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (state.inBin && state.notes.isNotEmpty()) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.empty_bin)) }, onClick = { menuOpen = false; onEmptyBin() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.labels)) }, onClick = { menuOpen = false; onOpenLabels() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menuOpen = false; onOpenSettings() })
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close_search)) }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, stringResource(R.string.clear_search)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    state: NotesListUiState,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onTogglePin: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onBin: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.clear_selection)) } },
        title = { Text(state.selection.size.toString()) },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, stringResource(R.string.select_all)) }
            if (state.inBin) {
                IconButton(onClick = onRestore) { Icon(Icons.Default.RestoreFromTrash, stringResource(R.string.restore)) }
                IconButton(onClick = onDeleteForever) { Icon(Icons.Default.DeleteForever, stringResource(R.string.delete_forever)) }
            } else {
                val allPinned = state.selectedNotes.all { it.note.pinned }
                IconButton(onClick = onTogglePin) {
                    Icon(Icons.Default.PushPin, stringResource(if (allPinned) R.string.unpin else R.string.pin_note))
                }
                val allLocked = state.selectedNotes.all { it.note.locked }
                IconButton(onClick = if (allLocked) onUnlock else onLock) {
                    if (allLocked) {
                        Icon(Icons.Default.LockOpen, stringResource(R.string.unlock_note))
                    } else {
                        Icon(Icons.Default.Lock, stringResource(R.string.lock_note))
                    }
                }
                IconButton(onClick = onBin) { Icon(Icons.Default.Delete, stringResource(R.string.move_to_bin)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelChips(labels: List<Label>, filter: NoteFilter, stacked: Boolean, onSelect: (NoteFilter) -> Unit) {
    val chips: @Composable () -> Unit = {
        FilterChip(
            selected = filter == NoteFilter.All,
            onClick = { onSelect(NoteFilter.All) },
            label = { Text(stringResource(R.string.all_notes)) },
        )
        labels.forEach { label ->
            FilterChip(
                selected = filter == NoteFilter.ByLabel(label.id),
                onClick = { onSelect(NoteFilter.ByLabel(label.id)) },
                label = { Text(label.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = when {
                    label.locked -> ({ Icon(Icons.Default.Lock, stringResource(R.string.locked_label), Modifier.size(18.dp)) })
                    label.folder -> ({ Icon(Icons.Default.Folder, stringResource(R.string.folder_label), Modifier.size(18.dp)) })
                    else -> null
                },
            )
        }
        FilterChip(
            selected = filter == NoteFilter.Bin,
            onClick = { onSelect(NoteFilter.Bin) },
            label = { Text(stringResource(R.string.bin)) },
            leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)) },
        )
    }
    if (stacked) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
    } else {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotesGrid(
    state: NotesListUiState,
    onClick: (NoteWithLabels) -> Unit,
    onLongClick: (NoteWithLabels) -> Unit,
) {
    val pinned = state.notes.filter { it.note.pinned }
    val others = state.notes.filterNot { it.note.pinned }
    // Section headers only make sense outside the bin and when both groups exist.
    val sectioned = !state.inBin && pinned.isNotEmpty() && others.isNotEmpty()
    val columns = if (state.settings.viewMode == ViewMode.List) StaggeredGridCells.Fixed(1) else StaggeredGridCells.Adaptive(160.dp)

    LazyVerticalStaggeredGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        // Bottom padding keeps the last row clear of the floating button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        if (sectioned) {
            item(key = "header-pinned", span = StaggeredGridItemSpan.FullLine) { SectionHeader(stringResource(R.string.pinned)) }
            items(pinned, key = { it.note.id }) { NoteCard(it, state, onClick, onLongClick) }
            item(key = "header-others", span = StaggeredGridItemSpan.FullLine) { SectionHeader(stringResource(R.string.others)) }
            items(others, key = { it.note.id }) { NoteCard(it, state, onClick, onLongClick) }
        } else {
            items(state.notes, key = { it.note.id }) { NoteCard(it, state, onClick, onLongClick) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    item: NoteWithLabels,
    state: NotesListUiState,
    onClick: (NoteWithLabels) -> Unit,
    onLongClick: (NoteWithLabels) -> Unit,
) {
    val note = item.note
    val selected = note.id in state.selection
    val preview = note.plainText.trim()
    // A locked note keeps its text out of the list until the PIN has been entered.
    val concealed = note.locked && !state.unlocked
    val showPreview = !concealed && preview.isNotEmpty() && (!state.settings.titleOnly || note.title.isBlank())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) }),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (note.title.isNotBlank() || note.pinned || note.locked) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        if (note.title.isBlank() && concealed) stringResource(R.string.locked_note) else note.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (note.locked) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Lock,
                            stringResource(R.string.locked_note),
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (note.pinned && !state.inBin) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.PushPin,
                            stringResource(R.string.pinned),
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (showPreview) {
                Text(
                    preview,
                    Modifier.padding(top = if (note.title.isNotBlank()) 6.dp else 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = state.settings.previewLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.labels.isNotEmpty()) {
                Text(
                    item.labels.sortedBy { it.position }.joinToString(" · ") { it.name },
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
