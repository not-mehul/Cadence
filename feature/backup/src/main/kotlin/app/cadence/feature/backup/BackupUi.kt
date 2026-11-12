package app.cadence.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.data.backup.BackupException
import app.cadence.core.data.backup.ImportMode
import app.cadence.core.domain.time.TodayProvider
import app.cadence.core.ui.components.CadenceListItem
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate
import app.cadence.core.ui.R as UiR

private const val MIN_PASSWORD_LENGTH = 8

/** The backup rows for Settings: export, restore, CSV and the clinician report. */
@Composable
fun BackupSettingsRows(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askPassword by rememberSaveable { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    val today = remember { TodayProvider.System.today() }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.exportBackup(uri, exportPassword)
            exportPassword = null
        }
    val csvLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri -> uri?.let(viewModel::exportCsv) }
    var askReportRange by rememberSaveable { mutableStateOf(false) }
    var reportRange by rememberSaveable { mutableStateOf(ReportRange.DEFAULT) }
    val reportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf"),
        ) { uri -> uri?.let { viewModel.exportReport(it, reportRange) } }
    val importLauncher = rememberImportLauncher(viewModel::startImport)

    // One job at a time: encrypting or restoring can take a few seconds, so the rows wait for it.
    val idle = !state.busy
    Column(modifier) {
        Row(R.string.backup_export, R.string.backup_export_detail, UiR.drawable.ms_backup, idle) { askPassword = true }
        Row(
            R.string.backup_import,
            R.string.backup_import_detail,
            UiR.drawable.ms_settings_backup_restore,
            idle,
        ) { importLauncher() }
        Row(R.string.backup_csv, R.string.backup_csv_detail, UiR.drawable.ms_table_view, idle) {
            csvLauncher.launch(fileName(today, "csv"))
        }
        Row(R.string.backup_report, R.string.backup_report_detail, UiR.drawable.ms_description, idle) {
            askReportRange = true
        }
        if (state.busy) Working()
    }

    if (askReportRange) {
        ReportRangeDialog(
            initial = reportRange,
            onContinue = { range ->
                askReportRange = false
                reportRange = range
                reportLauncher.launch(fileName(today, "pdf", "cadence-report"))
            },
            onDismiss = { askReportRange = false },
        )
    }
    if (askPassword) {
        ExportPasswordDialog(
            onContinue = { password ->
                askPassword = false
                exportPassword = password
                exportLauncher.launch(fileName(today, "json"))
            },
            onDismiss = { askPassword = false },
        )
    }
    BackupDialogs(state, viewModel.dialogActions())
}

/** "Restore from a backup" for onboarding: imports replace, since there's nothing here yet. */
@Composable
fun RestoreFromBackupButton(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    viewModel.alwaysReplace = true
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launch = rememberImportLauncher(viewModel::startImport)
    TextButton(onClick = launch, modifier = modifier, enabled = !state.busy) {
        Text(stringResource(R.string.backup_restore_onboarding))
    }
    BackupDialogs(state, viewModel.dialogActions())
}

@Composable
private fun rememberImportLauncher(onPick: (Uri) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPick) }
    // Some file managers label backups as plain JSON, others as generic files.
    return { launcher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
}

private fun fileName(
    today: LocalDate,
    extension: String,
    prefix: String = "cadence-backup",
) = "$prefix-$today.$extension"

@Composable
private fun Row(
    title: Int,
    detail: Int,
    @DrawableRes icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    CadenceListItem(
        headline = { Text(stringResource(title)) },
        supporting = { Text(stringResource(detail)) },
        icon = icon,
        modifier =
            Modifier.toggleable(value = false, enabled = enabled, role = Role.Button, onValueChange = { onClick() }),
    )
}

/** "Please wait…" under the rows while a backup, restore or export runs. Announced politely. */
@Composable
private fun Working() {
    val spacing = CadenceTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenGutter, vertical = spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            stringResource(R.string.backup_working),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    }
}

/** What the import and result dialogs can do. */
internal class BackupDialogActions(
    val askForMode: Boolean,
    val onPassword: (CharArray) -> Unit,
    val onMode: (ImportMode) -> Unit,
    val onCancelImport: () -> Unit,
    val onDismissMessage: () -> Unit,
)

private fun BackupViewModel.dialogActions() =
    BackupDialogActions(
        askForMode = !alwaysReplace,
        onPassword = ::submitPassword,
        onMode = ::confirmImport,
        onCancelImport = ::cancelImport,
        onDismissMessage = ::dismissMessage,
    )

@Composable
internal fun BackupDialogs(
    state: BackupUiState,
    actions: BackupDialogActions,
) {
    when (val pending = state.pendingImport) {
        is PendingImport.NeedsPassword -> {
            ImportPasswordDialog(
                pending.wrongPassword,
                onSubmit = actions.onPassword,
                onDismiss = actions.onCancelImport,
            )
        }

        is PendingImport.ChooseMode -> {
            if (actions.askForMode) {
                ImportModeDialog(
                    pending.data.entries.size,
                    onChoose = actions.onMode,
                    onDismiss = actions.onCancelImport,
                )
            }
        }

        null -> {}
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = actions.onDismissMessage,
            text = { Text(messageText(message)) },
            confirmButton = {
                TextButton(
                    onClick = actions.onDismissMessage,
                ) { Text(stringResource(R.string.backup_ok)) }
            },
        )
    }
}

@Composable
private fun messageText(message: BackupMessage): String =
    when (message) {
        BackupMessage.ExportDone -> {
            stringResource(R.string.backup_done_export)
        }

        BackupMessage.CsvDone -> {
            stringResource(R.string.backup_done_csv)
        }

        BackupMessage.ReportDone -> {
            stringResource(R.string.backup_done_report)
        }

        is BackupMessage.ImportDone -> {
            pluralStringResource(R.plurals.backup_done_import, message.days, message.days)
        }

        BackupMessage.WriteFailed -> {
            stringResource(R.string.backup_error_write)
        }

        BackupMessage.ReadFailed -> {
            stringResource(R.string.backup_error_read)
        }

        BackupMessage.RestoreFailed -> {
            stringResource(R.string.backup_error_restore)
        }

        is BackupMessage.ImportFailed -> {
            stringResource(
                when (message.reason) {
                    BackupException.Reason.NOT_A_BACKUP -> R.string.backup_error_not_backup

                    BackupException.Reason.NEWER_VERSION -> R.string.backup_error_newer

                    BackupException.Reason.WRONG_PASSWORD,
                    BackupException.Reason.PASSWORD_REQUIRED,
                    -> R.string.backup_error_password

                    BackupException.Reason.INVALID -> R.string.backup_error_invalid
                },
            )
        }
    }

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Optional password for the backup; empty means an unencrypted file. */
@Composable
private fun ExportPasswordDialog(
    onContinue: (CharArray?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Not saveable: passwords shouldn't be written to saved instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH
    val mismatch = password.isNotEmpty() && confirm != password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm)) {
                Text(stringResource(R.string.backup_password_body), style = MaterialTheme.typography.bodyMedium)
                PasswordField(
                    password,
                    { password = it },
                    R.string.backup_password_label,
                    error = if (tooShort) stringResource(R.string.backup_password_too_short) else null,
                )
                if (password.isNotEmpty()) {
                    PasswordField(
                        confirm,
                        { confirm = it },
                        R.string.backup_password_confirm_label,
                        error =
                            if (mismatch &&
                                confirm.isNotEmpty()
                            ) {
                                stringResource(R.string.backup_password_mismatch)
                            } else {
                                null
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !tooShort && !mismatch,
                onClick = { onContinue(password.takeIf { it.isNotEmpty() }?.toCharArray()) },
            ) { Text(stringResource(R.string.backup_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

@Composable
private fun ImportPasswordDialog(
    wrongPassword: Boolean,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_enter_password_title)) },
        text = {
            PasswordField(
                password,
                { password = it },
                R.string.backup_enter_password_label,
                error = if (wrongPassword) stringResource(R.string.backup_error_password) else null,
            )
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password.toCharArray()) }) {
                Text(stringResource(R.string.backup_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

@Composable
private fun ImportModeDialog(
    days: Int,
    onChoose: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_mode_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md)) {
                Text(pluralStringResource(R.plurals.backup_mode_body, days, days))
                ModeOption(
                    R.string.backup_mode_replace,
                    R.string.backup_mode_replace_detail,
                ) { onChoose(ImportMode.REPLACE) }
                ModeOption(R.string.backup_mode_merge, R.string.backup_mode_merge_detail) { onChoose(ImportMode.MERGE) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

@Composable
private fun ModeOption(
    title: Int,
    detail: Int,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** How far back the clinician report should look, asked before choosing where to save it. */
@Composable
internal fun ReportRangeDialog(
    initial: ReportRange,
    onContinue: (ReportRange) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_report_range_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ReportRange.entries.forEach { range ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = CadenceTheme.spacing.minTouchTarget)
                                .selectable(
                                    selected = range == chosen,
                                    role = Role.RadioButton,
                                    onClick = { chosen = range },
                                ),
                    ) {
                        RadioButton(selected = range == chosen, onClick = null)
                        Text(stringResource(rangeLabel(range)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onContinue(chosen) }) { Text(stringResource(R.string.backup_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

private fun rangeLabel(range: ReportRange) =
    when (range) {
        ReportRange.MONTHS_3 -> R.string.backup_report_range_3
        ReportRange.MONTHS_6 -> R.string.backup_report_range_6
        ReportRange.MONTHS_12 -> R.string.backup_report_range_12
        ReportRange.ALL -> R.string.backup_report_range_all
    }
