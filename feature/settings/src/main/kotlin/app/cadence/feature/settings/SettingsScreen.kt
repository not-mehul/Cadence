package app.cadence.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.ui.components.DayCountStepper
import app.cadence.core.ui.components.SectionHeader
import app.cadence.core.ui.theme.CadenceTheme
import app.cadence.core.ui.R as UiR

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings
    if (current == null) {
        Box(modifier.fillMaxSize())
        return
    }
    SettingsContent(
        settings = current,
        actions =
            SettingsActions(
                onCycleLength = viewModel::setCycleLength,
                onPeriodLength = viewModel::setPeriodLength,
                onGoal = viewModel::setGoal,
                onTheme = viewModel::setTheme,
                onDeleteAll = viewModel::deleteAllData,
            ),
        modifier = modifier,
    )
}

class SettingsActions(
    val onCycleLength: (Int) -> Unit,
    val onPeriodLength: (Int) -> Unit,
    val onGoal: (Goal) -> Unit,
    val onTheme: (ThemePreference) -> Unit,
    val onDeleteAll: () -> Unit,
)

private enum class SettingsDialog { NONE, CYCLE_LENGTH, PERIOD_LENGTH, MODE, THEME, DELETE }

@Composable
fun SettingsContent(
    settings: UserSettings,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }
    val defaults = settings.cycleDefaults
    val gutter = CadenceTheme.spacing.screenGutter

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = CadenceTheme.spacing.lg)) {
        SectionHeader(stringResource(R.string.settings_section_cycle), Modifier.padding(horizontal = gutter))
        SettingRow(
            title = stringResource(R.string.settings_cycle_length),
            value = pluralStringResource(UiR.plurals.days_count, defaults.cycleLength, defaults.cycleLength),
            onClick = { dialog = SettingsDialog.CYCLE_LENGTH },
        )
        SettingRow(
            title = stringResource(R.string.settings_period_length),
            value = pluralStringResource(UiR.plurals.days_count, defaults.periodLength, defaults.periodLength),
            onClick = { dialog = SettingsDialog.PERIOD_LENGTH },
        )
        Note(stringResource(R.string.settings_cycle_defaults_note))
        SettingRow(
            title = stringResource(R.string.settings_mode),
            value = stringResource(goalLabel(settings.goal)),
            onClick = { dialog = SettingsDialog.MODE },
        )

        Divider()
        SectionHeader(stringResource(R.string.settings_section_appearance), Modifier.padding(horizontal = gutter))
        SettingRow(
            title = stringResource(R.string.settings_theme),
            value = stringResource(themeLabel(settings.theme)),
            onClick = { dialog = SettingsDialog.THEME },
        )

        Divider()
        SectionHeader(stringResource(R.string.settings_section_privacy), Modifier.padding(horizontal = gutter))
        Note(stringResource(R.string.settings_privacy_statement))
        SettingRow(
            title = stringResource(R.string.settings_delete_all),
            value = stringResource(R.string.settings_delete_all_detail),
            onClick = { dialog = SettingsDialog.DELETE },
            titleColor = MaterialTheme.colorScheme.error,
        )

        Divider()
        SectionHeader(stringResource(R.string.settings_section_about), Modifier.padding(horizontal = gutter))
        Note(stringResource(R.string.settings_version, appVersion()))
        Note(stringResource(R.string.settings_disclaimer))
    }

    val close = { dialog = SettingsDialog.NONE }
    when (dialog) {
        SettingsDialog.NONE -> {}

        SettingsDialog.CYCLE_LENGTH -> {
            StepperDialog(
                title = stringResource(R.string.settings_cycle_length),
                label = stringResource(R.string.settings_cycle_length_label),
                initial = defaults.cycleLength,
                range = SettingsViewModel.cycleLengthRange(defaults),
                onSave = {
                    actions.onCycleLength(it)
                    close()
                },
                onDismiss = close,
            )
        }

        SettingsDialog.PERIOD_LENGTH -> {
            StepperDialog(
                title = stringResource(R.string.settings_period_length),
                label = stringResource(R.string.settings_period_length_label),
                initial = defaults.periodLength,
                range = SettingsViewModel.periodLengthRange(defaults),
                onSave = {
                    actions.onPeriodLength(it)
                    close()
                },
                onDismiss = close,
            )
        }

        SettingsDialog.MODE -> {
            ChoiceDialog(
                title = stringResource(R.string.settings_mode),
                options = Goal.entries,
                selected = settings.goal,
                label = { stringResource(goalLabel(it)) },
                detail = { stringResource(goalDetail(it)) },
                onSelect = {
                    actions.onGoal(it)
                    close()
                },
                onDismiss = close,
            )
        }

        SettingsDialog.THEME -> {
            ChoiceDialog(
                title = stringResource(R.string.settings_theme),
                options = ThemePreference.entries,
                selected = settings.theme,
                label = { stringResource(themeLabel(it)) },
                detail = { null },
                onSelect = {
                    actions.onTheme(it)
                    close()
                },
                onDismiss = close,
            )
        }

        SettingsDialog.DELETE -> {
            AlertDialog(
                onDismissRequest = close,
                title = { Text(stringResource(R.string.settings_delete_title)) },
                text = { Text(stringResource(R.string.settings_delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        close()
                        actions.onDeleteAll()
                    }) {
                        Text(stringResource(R.string.settings_delete_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.settings_cancel)) } },
            )
        }
    }
}

@StringRes
private fun goalLabel(goal: Goal) =
    when (goal) {
        Goal.TRACK -> R.string.settings_mode_track
        Goal.BIRTH_CONTROL -> R.string.settings_mode_birth_control
    }

@StringRes
private fun goalDetail(goal: Goal) =
    when (goal) {
        Goal.TRACK -> R.string.settings_mode_track_detail
        Goal.BIRTH_CONTROL -> R.string.settings_mode_birth_control_detail
    }

@StringRes
private fun themeLabel(theme: ThemePreference) =
    when (theme) {
        ThemePreference.SYSTEM -> R.string.settings_theme_system
        ThemePreference.LIGHT -> R.string.settings_theme_light
        ThemePreference.DARK -> R.string.settings_theme_dark
    }

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return context.packageManager
        .getPackageInfo(context.packageName, 0)
        .versionName
        .orEmpty()
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    titleColor: Color = Color.Unspecified,
) {
    ListItem(
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.selectable(selected = false, onClick = onClick, role = Role.Button),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = CadenceTheme.spacing.screenGutter, vertical = CadenceTheme.spacing.sm),
    )
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(vertical = CadenceTheme.spacing.sm))

@Composable
private fun StepperDialog(
    title: String,
    label: String,
    initial: Int,
    range: IntRange,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableIntStateOf(initial.coerceIn(range)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DayCountStepper(
                    value = value,
                    range = range,
                    onValueChange = { value = it.coerceIn(range) },
                    label = label,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    detail: @Composable (T) -> String?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == selected,
                                    onClick = { onSelect(option) },
                                    role = Role.RadioButton,
                                ).padding(vertical = CadenceTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Column {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            detail(option)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
