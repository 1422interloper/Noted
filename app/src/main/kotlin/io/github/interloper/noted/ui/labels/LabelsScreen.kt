package io.github.interloper.noted.ui.labels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.interloper.noted.R
import io.github.interloper.noted.data.Label
import io.github.interloper.noted.security.LockState
import io.github.interloper.noted.settings.Settings
import io.github.interloper.noted.ui.common.ConfirmDialog
import io.github.interloper.noted.ui.lock.PinSetupDialog
import io.github.interloper.noted.ui.lock.rememberUnlockRequester
import kotlinx.coroutines.launch

/** A label being created whose PIN still has to be chosen. */
private class PendingLocked(val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(
    settings: Settings,
    lockState: LockState,
    onBack: () -> Unit,
    viewModel: LabelsViewModel = viewModel(factory = LabelsViewModel.Factory),
) {
    val labels = viewModel.labels.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    val requestUnlock = rememberUnlockRequester(settings, lockState)

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Label?>(null) }
    var deleting by remember { mutableStateOf<Label?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var pendingLocked by remember { mutableStateOf<PendingLocked?>(null) }
    var nameTaken by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Renaming or deleting a locked label exposes or relabels hidden notes, so it needs the PIN.
    fun guarded(label: Label, action: () -> Unit) = if (label.locked) requestUnlock(action) else action()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.labels)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (labels != null && labels.any { !it.locked }) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_all_labels)) },
                                onClick = { menuOpen = false; confirmDeleteAll = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nameTaken = false; creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_label)) },
            )
        },
    ) { padding ->
        when {
            labels == null -> Unit
            labels.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_labels),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LabelList(
                labels = labels,
                onReorder = viewModel::reorder,
                onMove = viewModel::move,
                onRename = { label -> guarded(label) { nameTaken = false; renaming = label } },
                onDelete = { label -> guarded(label) { deleting = label } },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }

    if (creating) {
        LabelDialog(
            title = stringResource(R.string.new_label),
            initialName = "",
            chooseKind = true,
            nameTaken = nameTaken,
            onNameEdited = { nameTaken = false },
            onConfirm = { name, kind ->
                if (kind == LabelKind.Locked && !settings.hasPin) {
                    // The first locked label is where the PIN gets chosen.
                    creating = false
                    pendingLocked = PendingLocked(name)
                } else {
                    scope.launch { if (viewModel.create(name, kind)) creating = false else nameTaken = true }
                }
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { label ->
        LabelDialog(
            title = stringResource(R.string.rename_label),
            initialName = label.name,
            chooseKind = false,
            nameTaken = nameTaken,
            onNameEdited = { nameTaken = false },
            onConfirm = { name, _ ->
                scope.launch { if (viewModel.rename(label.id, name)) renaming = null else nameTaken = true }
            },
            onDismiss = { renaming = null },
        )
    }
    pendingLocked?.let { pending ->
        PinSetupDialog(
            title = stringResource(R.string.set_pin),
            onPinChosen = { hashed ->
                scope.launch {
                    viewModel.setPin(hashed)
                    lockState.unlock()
                    pendingLocked = null
                    if (!viewModel.create(pending.name, LabelKind.Locked)) {
                        // The name was taken in the meantime; reopen the dialog to pick another.
                        nameTaken = true
                        creating = true
                    }
                }
            },
            onDismiss = { pendingLocked = null },
        )
    }
    deleting?.let { label ->
        ConfirmDialog(
            title = stringResource(R.string.delete_label_title, label.name),
            text = stringResource(if (label.locked) R.string.delete_locked_label_text else R.string.delete_label_text),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = { viewModel.delete(label.id) },
            onDismiss = { deleting = null },
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.delete_all_labels),
            text = stringResource(R.string.delete_all_labels_text),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = viewModel::deleteAllUnlocked,
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

private val RowHeight = 56.dp

/**
 * The labels in order, reordered by dragging a row's handle. While a row is dragged the others slide out of its way;
 * the new order is stored when it is dropped.
 */
@Composable
private fun LabelList(
    labels: List<Label>,
    onReorder: (List<Long>) -> Unit,
    onMove: (id: Long, offset: Int) -> Unit,
    onRename: (Label) -> Unit,
    onDelete: (Label) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    // Shown order. It follows the stored labels, except that a drop is applied here first so the row lands at once.
    var order by remember { mutableStateOf(labels) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(labels) { if (draggingId == null) order = labels }

    val from = order.indexOfFirst { it.id == draggingId }
    val target = if (from < 0) -1 else (from + (dragOffset / rowPx).roundToInt()).coerceIn(order.indices)

    fun drop() {
        val start = order.indexOfFirst { it.id == draggingId }
        if (start >= 0) {
            val end = (start + (dragOffset / rowPx).roundToInt()).coerceIn(order.indices)
            if (end != start) {
                order = order.toMutableList().apply { add(end, removeAt(start)) }
                onReorder(order.map { it.id })
            }
        }
        draggingId = null
        dragOffset = 0f
    }

    // Bottom padding keeps the last row clear of the floating button.
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        order.forEachIndexed { index, label ->
            key(label.id) {
                val dragged = index == from
                val slideTarget = when {
                    from < 0 || dragged -> 0f
                    index in (from + 1)..target -> -rowPx
                    index in target until from -> rowPx
                    else -> 0f
                }
                val slide by animateFloatAsState(slideTarget, label = "label slide")
                LabelRow(
                    label = label,
                    dragged = dragged,
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    onMove = { onMove(label.id, it) },
                    onRename = { onRename(label) },
                    onDelete = { onDelete(label) },
                    // On drop the list is already in its new order, so the slide is dropped at once rather than animated.
                    modifier = Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) dragOffset else if (from >= 0) slide else 0f },
                    handleModifier = Modifier.pointerInput(label.id) {
                        detectDragGestures(
                            onDragStart = {
                                draggingId = label.id
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val start = order.indexOfFirst { it.id == label.id }
                                // The row cannot be dragged past either end of the list.
                                dragOffset = (dragOffset + amount.y).coerceIn(-start * rowPx, (order.lastIndex - start) * rowPx)
                            },
                            onDragEnd = { drop() },
                            onDragCancel = { drop() },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LabelRow(
    label: Label,
    dragged: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (offset: Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().height(RowHeight),
        color = if (dragged) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        shadowElevation = if (dragged) 6.dp else 0.dp,
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.DragHandle,
                    stringResource(R.string.drag_to_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (label.kind) {
                LabelKind.Locked -> Icon(Icons.Default.Lock, stringResource(R.string.locked_label))
                LabelKind.Folder -> Icon(Icons.Default.Folder, stringResource(R.string.folder_label))
                LabelKind.Plain -> Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
            }
            Text(
                label.name,
                Modifier.weight(1f).padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuOpen = false; onRename() })
                    // Reordering without dragging, for screen readers and switch access.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_up)) },
                        enabled = canMoveUp,
                        onClick = { menuOpen = false; onMove(-1) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_down)) },
                        enabled = canMoveDown,
                        onClick = { menuOpen = false; onMove(1) },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

/** Name entry for a new or renamed label; a new label also picks its kind, which cannot be changed later. */
@Composable
private fun LabelDialog(
    title: String,
    initialName: String,
    chooseKind: Boolean,
    nameTaken: Boolean,
    onNameEdited: () -> Unit,
    onConfirm: (name: String, kind: LabelKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(LabelKind.Plain) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onNameEdited() },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    isError = nameTaken,
                    supportingText = if (nameTaken) ({ Text(stringResource(R.string.label_name_taken)) }) else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (chooseKind) {
                    Spacer(Modifier.height(8.dp))
                    KindOption(LabelKind.Plain, kind, R.string.label_kind_plain, R.string.label_kind_plain_summary) { kind = it }
                    KindOption(LabelKind.Folder, kind, R.string.label_kind_folder, R.string.label_kind_folder_summary) { kind = it }
                    KindOption(LabelKind.Locked, kind, R.string.label_kind_locked, R.string.label_kind_locked_summary) { kind = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name, kind) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun KindOption(option: LabelKind, selected: LabelKind, title: Int, summary: Int, onSelect: (LabelKind) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
