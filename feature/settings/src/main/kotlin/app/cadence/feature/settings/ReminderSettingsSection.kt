package app.cadence.feature.settings

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.core.ui.components.CadenceListItem
import app.cadence.core.ui.format.currentLocale
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.cadence.core.ui.R as UiR

/** What the system allows, refreshed whenever Settings comes back into view. */
data class ReminderStatus(
    val notificationsAllowed: Boolean = true,
    /** The phone may pause Cadence in the background, delaying reminders. */
    val batteryRestricted: Boolean = false,
)

private enum class ReminderDialog { NONE, DAYS_BEFORE, PERIOD_TIME, DAILY_TIME }

@Composable
internal fun RemindersSection(
    reminders: ReminderSettings,
    status: ReminderStatus,
    actions: SettingsActions,
    onSchedule: Boolean = false,
) {
    var dialog by rememberSaveable { mutableStateOf(ReminderDialog.NONE) }
    val gutter = CadenceTheme.spacing.screenGutter
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale())

    /** Applies a change; turning a reminder on asks for notification permission if needed. */
    fun change(updated: ReminderSettings) {
        actions.onReminders(updated)
        val turnedOn = updated.anyEnabled && updated != reminders && !status.notificationsAllowed
        if (turnedOn) actions.onRequestNotificationPermission()
    }

    SettingsSection(stringResource(R.string.settings_section_reminders)) {
        if (onSchedule) {
            // Bleeds follow the pack, so period reminders don't apply; the birth control reminder is above.
            Text(
                stringResource(R.string.settings_reminder_on_schedule),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = gutter, vertical = CadenceTheme.spacing.sm),
            )
        } else {
            PeriodReminderRows(reminders, timeFormat, ::change) { dialog = it }
        }
        SwitchRow(
            icon = UiR.drawable.ms_notifications,
            title = stringResource(R.string.settings_reminder_daily),
            detail = stringResource(R.string.settings_reminder_daily_detail),
            checked = reminders.dailyEnabled,
            onCheckedChange = { change(reminders.copy(dailyEnabled = it)) },
        )
        if (reminders.dailyEnabled) {
            ValueRow(
                icon = UiR.drawable.ms_alarm,
                title = stringResource(R.string.settings_reminder_daily_time),
                value = timeFormat.format(reminders.dailyTime),
                onClick = { dialog = ReminderDialog.DAILY_TIME },
            )
        }
        SwitchRow(
            icon = UiR.drawable.ms_visibility_off,
            title = stringResource(R.string.settings_reminder_discreet),
            detail = stringResource(R.string.settings_reminder_discreet_detail),
            checked = reminders.discreet,
            onCheckedChange = { actions.onReminders(reminders.copy(discreet = it)) },
        )
        if (reminders.anyEnabled && !status.notificationsAllowed) {
            Warning(
                text = stringResource(R.string.settings_reminder_notifications_off),
                action = stringResource(R.string.settings_reminder_open_settings),
                onClick = actions.onOpenNotificationSettings,
                isError = true,
            )
        }
        if (reminders.anyEnabled && status.batteryRestricted) {
            Warning(
                text = stringResource(R.string.settings_reminder_battery),
                action = stringResource(R.string.settings_reminder_battery_action),
                onClick = actions.onOpenBatterySettings,
            )
        }
    }

    val close = { dialog = ReminderDialog.NONE }
    when (dialog) {
        ReminderDialog.NONE -> {}

        ReminderDialog.DAYS_BEFORE -> {
            StepperDialog(
                title = stringResource(R.string.settings_reminder_days_before),
                label = stringResource(R.string.settings_reminder_days_before),
                initial = reminders.periodDaysBefore,
                range = ReminderSettings.DAYS_BEFORE_RANGE,
                onSave = {
                    actions.onReminders(reminders.copy(periodDaysBefore = it))
                    close()
                },
                onDismiss = close,
            )
        }

        ReminderDialog.PERIOD_TIME -> {
            TimeDialog(
                stringResource(R.string.settings_reminder_period_time),
                reminders.periodTime,
                onDismiss = close,
            ) {
                actions.onReminders(reminders.copy(periodTime = it))
                close()
            }
        }

        ReminderDialog.DAILY_TIME -> {
            TimeDialog(stringResource(R.string.settings_reminder_daily_time), reminders.dailyTime, onDismiss = close) {
                actions.onReminders(reminders.copy(dailyTime = it))
                close()
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes icon: Int? = null,
) {
    CadenceListItem(
        headline = { Text(title) },
        supporting = { Text(detail) },
        icon = icon,
        trailing = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
internal fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
) {
    CadenceListItem(
        headline = { Text(title) },
        icon = icon,
        trailing = {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.toggleable(value = false, role = Role.Button, onValueChange = { onClick() }),
    )
}

@Composable
private fun Warning(
    text: String,
    action: String,
    onClick: () -> Unit,
    isError: Boolean = false,
) {
    Column(Modifier.padding(horizontal = CadenceTheme.spacing.screenGutter, vertical = CadenceTheme.spacing.xs)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClick) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onSave: (LocalTime) -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onSave(LocalTime.of(state.hour, state.minute))
            }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** Before-the-period and late-period reminders; not used on a birth control schedule. */
@Composable
private fun PeriodReminderRows(
    reminders: ReminderSettings,
    timeFormat: DateTimeFormatter,
    change: (ReminderSettings) -> Unit,
    open: (ReminderDialog) -> Unit,
) {
    Column {
        SwitchRow(
            icon = UiR.drawable.ms_water_drop,
            title = stringResource(R.string.settings_reminder_period),
            detail =
                pluralStringResource(
                    R.plurals.settings_reminder_period_detail,
                    reminders.periodDaysBefore,
                    reminders.periodDaysBefore,
                ),
            checked = reminders.periodEnabled,
            onCheckedChange = { change(reminders.copy(periodEnabled = it)) },
        )
        if (reminders.periodEnabled) {
            ValueRow(
                icon = UiR.drawable.ms_alarm,
                title = stringResource(R.string.settings_reminder_days_before),
                value =
                    pluralStringResource(
                        R.plurals.settings_reminder_days_value,
                        reminders.periodDaysBefore,
                        reminders.periodDaysBefore,
                    ),
                onClick = { open(ReminderDialog.DAYS_BEFORE) },
            )
        }
        SwitchRow(
            icon = UiR.drawable.ms_event_busy,
            title = stringResource(R.string.settings_reminder_late),
            detail = stringResource(R.string.settings_reminder_late_detail),
            checked = reminders.lateEnabled,
            onCheckedChange = { change(reminders.copy(lateEnabled = it)) },
        )
        if (reminders.periodEnabled || reminders.lateEnabled) {
            ValueRow(
                icon = UiR.drawable.ms_alarm,
                title = stringResource(R.string.settings_reminder_period_time),
                value = timeFormat.format(reminders.periodTime),
                onClick = { open(ReminderDialog.PERIOD_TIME) },
            )
        }
    }
}
