package io.github.interloper.noted.ui.editor

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichText
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import io.github.interloper.noted.R
import io.github.interloper.noted.data.Label
import io.github.interloper.noted.data.NoteWithLabels
import io.github.interloper.noted.domain.textStats
import io.github.interloper.noted.domain.toggleLabelSelection
import io.github.interloper.noted.security.LockState
import io.github.interloper.noted.settings.Settings
import io.github.interloper.noted.ui.common.ConfirmDialog
import io.github.interloper.noted.ui.common.formatDateTime
import io.github.interloper.noted.ui.lock.PinSetupDialog
import io.github.interloper.noted.ui.lock.rememberUnlockRequester

/**
 * Edits one note, saving as the user types.
 *
 * @param noteId the note to open, or null to start a new one.
 * @param newNoteLabelId label a new note starts with.
 * @param onBinned called instead of [onBack] when the note was moved to the bin, so the list can offer an undo.
 * @param onLocked called when the user locks the note from here; it stays open until they leave it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalRichTextApi::class)
@Composable
fun EditorScreen(
    noteId: Long?,
    newNoteLabelId: Long?,
    settings: Settings,
    lockState: LockState,
    onBack: () -> Unit,
    onBinned: (noteId: Long) -> Unit,
    onLocked: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(noteId, newNoteLabelId)),
) {
    val load by viewModel.load.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val labelIds by viewModel.labelIds.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val requestUnlock = rememberUnlockRequester(settings, lockState)
    var pinSetup by remember { mutableStateOf(false) }
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Not saved with instance state: a long note would overflow it. The ViewModel holds the text instead.
    val richState = remember { RichTextState() }
    var seeded by remember { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var labelsDialog by remember { mutableStateOf(false) }
    var infoSheet by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    val ready = load as? EditorLoad.Ready
    val binned = note?.note?.isBinned ?: (ready?.binned == true)
    val editable = seeded && !binned && !reading

    LaunchedEffect(load) {
        when (val state = load) {
            EditorLoad.Loading -> Unit
            EditorLoad.Missing -> onBack()
            is EditorLoad.Ready -> if (!seeded) {
                if (viewModel.editorInitialised) {
                    // The screen was recreated; carry on from the text the ViewModel already has.
                    richState.setHtml(viewModel.bodyHtml)
                } else {
                    richState.setHtml(state.content)
                    viewModel.onEditorInitialised(richState.toHtml(), richState.toText())
                    if (noteId == null) titleFocus.requestFocus()
                }
                richState.history.clear()
                seeded = true
            }
        }
    }
    LaunchedEffect(seeded) {
        if (!seeded) return@LaunchedEffect
        // Emits for text and formatting changes, not for cursor movement.
        snapshotFlow { richState.annotatedString }.collect {
            viewModel.onBodyChanged(richState.toHtml(), richState.toText())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    val colors = MaterialTheme.colorScheme
    LaunchedEffect(colors) {
        richState.config.linkColor = colors.primary
        richState.config.codeSpanColor = colors.onSurfaceVariant
        richState.config.codeSpanBackgroundColor = colors.surfaceContainerHighest
        richState.config.codeSpanStrokeColor = colors.outlineVariant
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (binned) {
                        IconButton(onClick = { viewModel.restore(onBack) }) {
                            Icon(Icons.Default.RestoreFromTrash, stringResource(R.string.restore))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.DeleteForever, stringResource(R.string.delete_forever))
                        }
                    } else if (seeded) {
                        if (!reading) {
                            IconButton(onClick = { richState.history.undo() }, enabled = richState.history.canUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
                            }
                            IconButton(onClick = { richState.history.redo() }, enabled = richState.history.canRedo) {
                                Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo))
                            }
                        }
                        IconButton(onClick = viewModel::togglePin) {
                            Icon(
                                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                stringResource(if (pinned) R.string.unpin else R.string.pin_note),
                            )
                        }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            @Composable
                            fun item(label: Int, onClick: () -> Unit) = DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                onClick = { menuOpen = false; onClick() },
                            )
                            item(if (reading) R.string.edit_note else R.string.reading_mode) { reading = !reading }
                            item(R.string.labels) { labelsDialog = true }
                            if (locked) {
                                item(R.string.unlock_note) { requestUnlock { viewModel.setLocked(false) } }
                            } else {
                                item(R.string.lock_note) {
                                    if (settings.hasPin) {
                                        onLocked()
                                        viewModel.setLocked(true)
                                    } else {
                                        // The first lock is where the PIN gets chosen.
                                        pinSetup = true
                                    }
                                }
                            }
                            item(R.string.share) { shareNote(context, viewModel.title, richState.toText()) }
                            item(R.string.note_info) { infoSheet = true }
                            if (!reading) item(R.string.clear_content) { confirmClear = true }
                            item(R.string.move_to_bin) {
                                viewModel.moveToBin { binnedId -> if (binnedId != null) onBinned(binnedId) else onBack() }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = { if (editable) FormattingToolbar(richState) },
    ) { padding ->
        if (!seeded) return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            val titleStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.onSurface)
            BasicTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(titleFocus),
                readOnly = !editable,
                textStyle = titleStyle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { field ->
                    Box {
                        if (viewModel.title.isEmpty()) {
                            Text(stringResource(R.string.title_hint), style = titleStyle, color = colors.onSurfaceVariant)
                        }
                        field()
                    }
                },
            )
            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
            if (!editable) {
                // Reading mode and binned notes: text can be selected and copied, and links open when tapped.
                val uriHandler = rememberSafeUriHandler()
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        BasicRichText(
                            state = richState,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                            style = bodyStyle,
                        )
                    }
                }
            } else BasicRichTextEditor(
                state = richState,
                // Fills the rest of the screen and scrolls inside it, so a tap anywhere below the text lands in the note.
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                readOnly = !editable,
                textStyle = bodyStyle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { field ->
                    Box {
                        if (richState.annotatedString.text.isEmpty()) {
                            Text(stringResource(R.string.note_hint), style = bodyStyle, color = colors.onSurfaceVariant)
                        }
                        field()
                    }
                },
            )
        }
    }

    if (labelsDialog) {
        LabelsDialog(
            labels = labels,
            initial = labelIds,
            settings = settings,
            lockState = lockState,
            onConfirm = viewModel::setLabels,
            onDismiss = { labelsDialog = false },
        )
    }
    if (pinSetup) {
        PinSetupDialog(
            title = stringResource(R.string.set_pin),
            onPinChosen = { hashed ->
                pinSetup = false
                onLocked()
                viewModel.setPinAndLock(hashed)
            },
            onDismiss = { pinSetup = false },
        )
    }
    if (infoSheet) {
        InfoSheet(note = note, labelCount = labelIds.size, text = richState.toText(), onDismiss = { infoSheet = false })
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_content),
            text = stringResource(R.string.clear_content_text),
            confirmLabel = stringResource(R.string.clear),
            destructive = true,
            onConfirm = { richState.clear() },
            onDismiss = { confirmClear = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_forever_title),
            text = stringResource(R.string.delete_note_forever_text),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = { viewModel.deleteForever(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Opens links in whatever app handles them; an address nothing can open is ignored rather than crashing. */
@Composable
internal fun rememberSafeUriHandler(): UriHandler {
    val platform = LocalUriHandler.current
    return remember(platform) {
        object : UriHandler {
            override fun openUri(uri: String) {
                try {
                    platform.openUri(uri)
                } catch (_: IllegalArgumentException) {
                    // No app for this address, or it is malformed.
                }
            }
        }
    }
}

private fun shareNote(context: Context, title: String, body: String) {
    val text = listOf(title.trim(), body.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
    if (text.isEmpty()) return
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .putExtra(Intent.EXTRA_SUBJECT, title.trim())
    context.startActivity(Intent.createChooser(send, null))
}

/** Picks the note's labels; the change is applied when the dialog is confirmed. */
@Composable
private fun LabelsDialog(
    labels: List<Label>,
    initial: Set<Long>,
    settings: Settings,
    lockState: LockState,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(initial) }
    val requestUnlock = rememberUnlockRequester(settings, lockState)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.labels)) },
        text = {
            if (labels.isEmpty()) {
                Text(stringResource(R.string.no_labels))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    labels.forEach { label ->
                        val checked = label.id in selection
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = checked, role = Role.Checkbox) {
                                    val toggle = { selection = toggleLabelSelection(selection, label, labels) }
                                    // Putting a note under a locked label hides it, so that needs the PIN first.
                                    if (label.locked && !checked) requestUnlock(toggle) else toggle()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                label.name,
                                Modifier.weight(1f).padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            when {
                                label.locked -> Icon(Icons.Default.Lock, stringResource(R.string.locked_label), Modifier.size(18.dp))
                                label.folder -> Icon(Icons.Default.Folder, stringResource(R.string.folder_label), Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selection); onDismiss() }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(note: NoteWithLabels?, labelCount: Int, text: String, onDismiss: () -> Unit) {
    val stats = remember(text) { textStats(text) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.note_info), Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.titleLarge)
            // A new note has no dates until its first save.
            if (note != null) {
                InfoRow(stringResource(R.string.info_created), formatDateTime(note.note.createdAt))
                InfoRow(stringResource(R.string.info_edited), formatDateTime(note.note.editedAt))
            }
            InfoRow(stringResource(R.string.info_words), stats.words.toString())
            InfoRow(stringResource(R.string.info_characters), stats.characters.toString())
            InfoRow(stringResource(R.string.info_labels), labelCount.toString())
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
