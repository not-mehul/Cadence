package app.cadence.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.core.domain.security.SecuritySettings
import app.cadence.core.ui.components.DayCountStepper
import app.cadence.core.ui.components.SectionHeader
import app.cadence.core.ui.log.LogLabels
import app.cadence.core.ui.security.DeviceAuth
import app.cadence.core.ui.theme.CadenceTheme
import app.cadence.feature.backup.BackupSettingsRows
import app.cadence.core.ui.R as UiR

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentActivity = LocalActivity.current
    var status by remember { mutableStateOf(readReminderStatus(context)) }
    LifecycleResumeEffect(Unit) {
        // Permission or battery settings may have changed in system settings.
        status = readReminderStatus(context)
        onPauseOrDispose {}
    }
    var noScreenLock by remember { mutableStateOf(false) }
    val lockPromptTitle = stringResource(R.string.settings_lock_prompt)
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            status =
                readReminderStatus(context)
        }
    val current = settings
    if (current == null) {
        Box(modifier.fillMaxSize())
        return
    }
    SettingsContent(
        settings = current,
        reminderStatus = status,
        actions =
            SettingsActions(
                onCycleLength = viewModel::setCycleLength,
                onPeriodLength = viewModel::setPeriodLength,
                onGoal = viewModel::setGoal,
                onTheme = viewModel::setTheme,
                onHiddenCategories = viewModel::setHiddenCategories,
                onReminders = viewModel::setReminders,
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenNotificationSettings = {
                    context.openSettings(
                        Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                        ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
                onOpenBatterySettings = {
                    context.openSettings(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                },
                onDeleteAll = viewModel::deleteAllData,
                onLockChange = { enable ->
                    val activity = currentActivity as? FragmentActivity
                    if (activity == null || !DeviceAuth.isAvailable(context)) {
                        noScreenLock = true
                    } else {
                        // Confirm it's really the owner, both to turn the lock on and to turn it off.
                        DeviceAuth.authenticate(
                            activity,
                            lockPromptTitle,
                            null,
                            onSuccess = { viewModel.setLockEnabled(enable) },
                        )
                    }
                },
                onLockTimeout = viewModel::setLockTimeout,
                onBlockScreenshots = viewModel::setBlockScreenshots,
            ),
        modifier = modifier,
        backupRows = { BackupSettingsRows() },
    )
    if (noScreenLock) {
        AlertDialog(
            onDismissRequest = { noScreenLock = false },
            title = { Text(stringResource(R.string.settings_lock_unavailable_title)) },
            text = { Text(stringResource(R.string.settings_lock_unavailable_body)) },
            confirmButton = {
                TextButton(
                    onClick = { noScreenLock = false },
                ) { Text(stringResource(R.string.settings_ok)) }
            },
        )
    }
}

class SettingsActions(
    val onCycleLength: (Int) -> Unit,
    val onPeriodLength: (Int) -> Unit,
    val onGoal: (Goal) -> Unit,
    val onTheme: (ThemePreference) -> Unit,
    val onHiddenCategories: (Set<LogCategory>) -> Unit,
    val onDeleteAll: () -> Unit,
    val onReminders: (ReminderSettings) -> Unit = {},
    val onRequestNotificationPermission: () -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    val onOpenBatterySettings: () -> Unit = {},
    val onLockChange: (Boolean) -> Unit = {},
    val onLockTimeout: (Int) -> Unit = {},
    val onBlockScreenshots: (Boolean) -> Unit = {},
)

private fun readReminderStatus(context: Context): ReminderStatus {
    val permitted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(PowerManager::class.java)
    return ReminderStatus(
        notificationsAllowed = permitted && NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryRestricted = !power.isIgnoringBatteryOptimizations(context.packageName),
    )
}

private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some phones don't have this screen; nothing more we can do.
    }
}

private enum class SettingsDialog {
    NONE,
    CYCLE_LENGTH,
    PERIOD_LENGTH,
    MODE,
    THEME,
    CATEGORIES,
    LOCK_TIMEOUT,
    PRIVACY,
    DELETE,
}

@Composable
fun SettingsContent(
    settings: UserSettings,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    reminderStatus: ReminderStatus = ReminderStatus(),
    backupRows: @Composable () -> Unit = {},
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
        SectionHeader(stringResource(R.string.settings_section_log), Modifier.padding(horizontal = gutter))
        val hideable = LogCategory.entries.filter { it.canBeHidden }
        val shown = hideable.count { it !in settings.hiddenCategories }
        SettingRow(
            title = stringResource(R.string.settings_log_categories),
            value = pluralStringResource(R.plurals.settings_log_categories_value, hideable.size, shown, hideable.size),
            onClick = { dialog = SettingsDialog.CATEGORIES },
        )

        Divider()
        RemindersSection(settings.reminders, reminderStatus, actions)

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
        val security = settings.security
        SwitchRow(
            title = stringResource(R.string.settings_lock),
            detail = stringResource(R.string.settings_lock_detail),
            checked = security.lockEnabled,
            onCheckedChange = actions.onLockChange,
        )
        if (security.lockEnabled) {
            SettingRow(
                title = stringResource(R.string.settings_lock_timeout),
                value = stringResource(lockTimeoutLabel(security.lockTimeoutMinutes)),
                onClick = { dialog = SettingsDialog.LOCK_TIMEOUT },
            )
        }
        SwitchRow(
            title = stringResource(R.string.settings_block_screenshots),
            detail = stringResource(R.string.settings_block_screenshots_detail),
            checked = security.blockScreenshots,
            onCheckedChange = actions.onBlockScreenshots,
        )
        SettingRow(
            title = stringResource(R.string.settings_privacy_explainer),
            value = stringResource(R.string.settings_privacy_explainer_detail),
            onClick = { dialog = SettingsDialog.PRIVACY },
        )
        backupRows()
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

        SettingsDialog.CATEGORIES -> {
            CategoriesDialog(
                hidden = settings.hiddenCategories,
                onSave = {
                    actions.onHiddenCategories(it)
                    close()
                },
                onDismiss = close,
            )
        }

        SettingsDialog.LOCK_TIMEOUT -> {
            ChoiceDialog(
                title = stringResource(R.string.settings_lock_timeout),
                options = SecuritySettings.LOCK_TIMEOUT_OPTIONS,
                selected = settings.security.lockTimeoutMinutes,
                label = { stringResource(lockTimeoutLabel(it)) },
                detail = { null },
                onSelect = {
                    actions.onLockTimeout(it)
                    close()
                },
                onDismiss = close,
            )
        }

        SettingsDialog.PRIVACY -> {
            PrivacyExplainerDialog(onDismiss = close)
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
internal fun StepperDialog(
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

@Composable
private fun CategoriesDialog(
    hidden: Set<LogCategory>,
    onSave: (Set<LogCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    var hiddenNow by rememberSaveable { mutableStateOf(hidden.map { it.name }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_log_categories)) },
        text = {
            // Scrolls on short screens, where eight rows and the note don't fit.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.settings_log_categories_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LogCategory.entries.filter { it.canBeHidden }.forEach { category ->
                    val checked = category.name !in hiddenNow
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = { show ->
                                        hiddenNow = if (show) hiddenNow - category.name else hiddenNow + category.name
                                    },
                                ).padding(vertical = CadenceTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(stringResource(LogLabels.category(category)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(LogCategory.entries.filter { it.name in hiddenNow }.toSet()) }) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@StringRes
private fun lockTimeoutLabel(minutes: Int) =
    when (minutes) {
        0 -> R.string.settings_lock_immediately
        1 -> R.string.settings_lock_1_minute
        else -> R.string.settings_lock_5_minutes
    }

/** What Cadence does to protect data, in plain language (docs/PLAN.md Phase 5). */
@Composable
private fun PrivacyExplainerDialog(onDismiss: () -> Unit) {
    val points =
        listOf(
            R.string.privacy_point_device,
            R.string.privacy_point_encrypted,
            R.string.privacy_point_no_internet,
            R.string.privacy_point_no_backup,
            R.string.privacy_point_lock,
            R.string.privacy_point_notifications,
            R.string.privacy_point_exports,
            R.string.privacy_point_delete,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_privacy_explainer)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
            ) {
                points.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) } },
    )
}
