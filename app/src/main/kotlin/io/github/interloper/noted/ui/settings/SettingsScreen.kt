package io.github.interloper.noted.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.interloper.noted.R
import io.github.interloper.noted.backup.BackupFiles
import io.github.interloper.noted.security.LockState
import io.github.interloper.noted.settings.AppFont
import io.github.interloper.noted.settings.BinRetention
import io.github.interloper.noted.settings.Settings
import io.github.interloper.noted.settings.ThemeMode
import io.github.interloper.noted.ui.common.ConfirmDialog
import io.github.interloper.noted.ui.common.ListRow
import io.github.interloper.noted.ui.common.RadioDialog
import io.github.interloper.noted.ui.lock.PinEntryDialog
import io.github.interloper.noted.ui.lock.PinSetupDialog
import io.github.interloper.noted.ui.lock.canUseBiometrics
import io.github.interloper.noted.ui.theme.SeedColors
import io.github.interloper.noted.ui.theme.isDark
import io.github.interloper.noted.ui.theme.supportsDynamicColor
import java.time.LocalDate

private const val UPSTREAM_URL = "https://github.com/PranshulGG/NotesMaster"

private enum class Choice { Theme, Font, PreviewLines, BinRetention }

private enum class PinStep { VerifyToChange, ChooseNew, VerifyToRemove, ConfirmRemove }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    lockState: LockState,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var choice by remember { mutableStateOf<Choice?>(null) }
    var pinStep by remember { mutableStateOf<PinStep?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFiles.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    // Any type: file managers often report .json as plain text or a generic binary.
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restore(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setBackupFolder(uri)
    }

    val exported = stringResource(R.string.backup_exported)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val notABackup = stringResource(R.string.backup_not_a_backup)
    val restoreFailed = stringResource(R.string.backup_restore_failed)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    BackupMessage.Exported -> exported
                    BackupMessage.ExportFailed -> exportFailed
                    BackupMessage.NotABackup -> notABackup
                    BackupMessage.RestoreFailed -> restoreFailed
                    is BackupMessage.Restored -> resources.getString(
                        R.string.backup_restored,
                        message.result.notesAdded,
                        message.result.notesSkipped,
                        message.result.labelsAdded,
                    )
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Section(R.string.settings_appearance)
            ListRow(
                title = stringResource(R.string.settings_theme),
                summary = stringResource(settings.themeMode.labelRes),
                onClick = { choice = Choice.Theme },
            )
            SwitchRow(
                title = stringResource(R.string.settings_amoled),
                summary = stringResource(R.string.settings_amoled_summary),
                checked = settings.amoled,
                enabled = settings.themeMode != ThemeMode.Light,
                onChange = { viewModel.update { setAmoled(it) } },
            )
            if (supportsDynamicColor) {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = settings.dynamicColor,
                    onChange = { viewModel.update { setDynamicColor(it) } },
                )
            }
            if (!settings.dynamicColor || !supportsDynamicColor) {
                SeedColorRow(selected = settings.seedColor, onSelect = { viewModel.update { setSeedColor(it) } })
            }
            ListRow(
                title = stringResource(R.string.settings_font),
                summary = stringResource(settings.font.labelRes),
                onClick = { choice = Choice.Font },
            )

            Section(R.string.settings_note_list)
            SwitchRow(
                title = stringResource(R.string.settings_title_only),
                summary = stringResource(R.string.settings_title_only_summary),
                checked = settings.titleOnly,
                onChange = { viewModel.update { setTitleOnly(it) } },
            )
            ListRow(
                title = stringResource(R.string.settings_preview_lines),
                summary = settings.previewLines.toString(),
                enabled = !settings.titleOnly,
                onClick = { choice = Choice.PreviewLines },
            )
            SwitchRow(
                title = stringResource(R.string.settings_stacked_labels),
                summary = stringResource(R.string.settings_stacked_labels_summary),
                checked = settings.stackedLabels,
                onChange = { viewModel.update { setStackedLabels(it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_remember_label),
                summary = stringResource(R.string.settings_remember_label_summary),
                checked = settings.rememberLastLabel,
                onChange = { viewModel.update { setRememberLastLabel(it) } },
            )

            Section(R.string.bin)
            ListRow(
                title = stringResource(R.string.settings_bin_retention),
                summary = stringResource(settings.binRetention.labelRes),
                onClick = { choice = Choice.BinRetention },
            )

            Section(R.string.settings_locked_notes)
            ListRow(
                title = stringResource(if (settings.hasPin) R.string.change_pin else R.string.set_pin),
                summary = stringResource(R.string.settings_pin_summary),
                onClick = { pinStep = if (settings.hasPin) PinStep.VerifyToChange else PinStep.ChooseNew },
            )
            if (settings.hasPin) {
                ListRow(
                    title = stringResource(R.string.remove_pin),
                    summary = stringResource(R.string.remove_pin_summary),
                    onClick = { pinStep = PinStep.VerifyToRemove },
                )
            }
            val biometricsAvailable = remember { canUseBiometrics(context) }
            SwitchRow(
                title = stringResource(R.string.settings_biometric),
                summary = stringResource(
                    if (biometricsAvailable) R.string.settings_biometric_summary else R.string.settings_biometric_unavailable,
                ),
                checked = settings.biometricUnlock && biometricsAvailable,
                enabled = settings.hasPin && biometricsAvailable,
                onChange = { viewModel.update { setBiometricUnlock(it) } },
            )

            Section(R.string.settings_backup)
            ListRow(
                title = stringResource(R.string.backup_export),
                summary = stringResource(R.string.backup_export_summary),
                onClick = { exportLauncher.launchSafely(context, "noted-backup-${LocalDate.now()}.json") },
            )
            ListRow(
                title = stringResource(R.string.backup_restore),
                summary = stringResource(R.string.backup_restore_summary),
                onClick = { restoreLauncher.launchSafely(context, arrayOf("*/*")) },
            )
            val folder = settings.backupFolderUri
            SwitchRow(
                title = stringResource(R.string.backup_auto),
                summary = stringResource(R.string.backup_auto_summary),
                checked = settings.autoBackup,
                onChange = { on ->
                    // Turning it on without a folder asks for one; picking it switches auto-backup on.
                    if (on && folder == null) folderLauncher.launchSafely(context, null) else viewModel.update { setAutoBackup(on) }
                },
            )
            if (folder != null) {
                val folderName = remember(folder) { BackupFiles.folderName(context, folder) }
                ListRow(
                    title = stringResource(R.string.backup_folder),
                    summary = folderName ?: stringResource(R.string.backup_folder_unavailable),
                    onClick = { folderLauncher.launchSafely(context, null) },
                    trailing = {
                        IconButton(onClick = viewModel::clearBackupFolder) {
                            Icon(Icons.Default.Close, stringResource(R.string.backup_folder_clear))
                        }
                    },
                )
            }

            Section(R.string.settings_about)
            ListRow(title = stringResource(R.string.app_name), summary = remember { versionName(context) })
            ListRow(
                title = stringResource(R.string.about_upstream),
                summary = stringResource(R.string.about_upstream_summary),
                onClick = { openUrl(context, UPSTREAM_URL) },
            )
        }
    }

    when (choice) {
        Choice.Theme -> RadioDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(it.labelRes) },
            onSelect = { viewModel.update { setThemeMode(it) } },
            onDismiss = { choice = null },
        )
        Choice.Font -> RadioDialog(
            title = stringResource(R.string.settings_font),
            options = AppFont.entries,
            selected = settings.font,
            label = { stringResource(it.labelRes) },
            onSelect = { viewModel.update { setFont(it) } },
            onDismiss = { choice = null },
        )
        Choice.PreviewLines -> RadioDialog(
            title = stringResource(R.string.settings_preview_lines),
            options = Settings.PREVIEW_LINES_RANGE.toList(),
            selected = settings.previewLines,
            label = { it.toString() },
            onSelect = { viewModel.update { setPreviewLines(it) } },
            onDismiss = { choice = null },
        )
        Choice.BinRetention -> RadioDialog(
            title = stringResource(R.string.settings_bin_retention),
            options = BinRetention.entries,
            selected = settings.binRetention,
            label = { stringResource(it.labelRes) },
            onSelect = { viewModel.update { setBinRetention(it) } },
            onDismiss = { choice = null },
        )
        null -> Unit
    }

    when (pinStep) {
        PinStep.VerifyToChange -> PinEntryDialog(
            settings = settings,
            title = stringResource(R.string.current_pin),
            onVerified = { pinStep = PinStep.ChooseNew },
            onDismiss = { pinStep = null },
        )
        PinStep.VerifyToRemove -> PinEntryDialog(
            settings = settings,
            title = stringResource(R.string.current_pin),
            onVerified = { pinStep = PinStep.ConfirmRemove },
            onDismiss = { pinStep = null },
        )
        PinStep.ConfirmRemove -> ConfirmDialog(
            title = stringResource(R.string.remove_pin_title),
            text = stringResource(R.string.remove_pin_text),
            confirmLabel = stringResource(R.string.remove_pin),
            destructive = true,
            onConfirm = viewModel::removePin,
            onDismiss = { pinStep = null },
        )
        PinStep.ChooseNew -> PinSetupDialog(
            title = stringResource(if (settings.hasPin) R.string.change_pin else R.string.set_pin),
            onPinChosen = { hashed ->
                viewModel.update { setPin(hashed.hash, hashed.salt) }
                // Whoever just chose the PIN does not need to type it again straight away.
                lockState.unlock()
                pinStep = null
            },
            onDismiss = { pinStep = null },
        )
        null -> Unit
    }
}

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

private val AppFont.labelRes: Int
    get() = when (this) {
        AppFont.System -> R.string.font_system
        AppFont.Serif -> R.string.font_serif
        AppFont.Monospace -> R.string.font_monospace
    }

private val BinRetention.labelRes: Int
    get() = when (this) {
        BinRetention.Never -> R.string.retention_never
        BinRetention.OneDay -> R.string.retention_one_day
        BinRetention.OneWeek -> R.string.retention_one_week
        BinRetention.TwoWeeks -> R.string.retention_two_weeks
        BinRetention.OneMonth -> R.string.retention_one_month
    }

/** Launches a system picker; a device without a files app just gets nothing rather than a crash. */
private fun <I> androidx.activity.result.ActivityResultLauncher<I>.launchSafely(context: Context, input: I) {
    try {
        launch(input)
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, R.string.no_files_app, android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // No browser installed; the address is shown in the row's summary.
    }
}

private fun versionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

@Composable
private fun Section(title: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        stringResource(title),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListRow(
        title = title,
        summary = summary,
        enabled = enabled,
        // The whole row is the switch for touch and for screen readers.
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
private fun SeedColorRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.settings_seed_color),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SeedColors.forEachIndexed { index, seed ->
                val isSelected = seed == selected
                val description = stringResource(R.string.settings_seed_color_option, index + 1)
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(seed))
                        .border(if (isSelected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable(role = Role.RadioButton) { onSelect(seed) }
                        .semantics {
                            contentDescription = description
                            this.selected = isSelected
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}
