package app.cadence.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.ui.components.DayCountStepper
import app.cadence.core.ui.components.NumberStepper
import app.cadence.core.ui.components.SectionHeader
import app.cadence.core.ui.contraception.ContraceptionLabels
import app.cadence.core.ui.format.currentLocale
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Phase 6 data Settings shows besides [app.cadence.core.domain.model.UserSettings]. */
data class TrackingSettingsState(
    val today: LocalDate,
    val medications: List<Medication> = emptyList(),
    val tags: List<CustomTag> = emptyList(),
)

/** What the birth control dialog asks for; turned into a schedule by [SettingsViewModel.scheduleFrom]. */
data class BirthControlChoice(
    /** Null: no pill, patch or ring (an IUD, implant or injection, say). */
    val method: ContraceptiveMethod?,
    val activeDays: Int = 21,
    val breakDays: Int = 7,
    val placebo: Boolean = false,
    /** Which day of the pack today is. */
    val dayToday: Int = 1,
)

@Composable
private fun timeText(time: LocalTime): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(time)

// --- Birth control ---------------------------------------------------------------------

/** Shown in birth control mode, under the mode row. */
@Composable
internal fun BirthControlRows(
    schedule: ContraceptiveSchedule?,
    today: LocalDate,
    actions: SettingsActions,
    notificationsAllowed: Boolean,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        title = stringResource(R.string.settings_bc),
        value = schedule?.let { ContraceptionLabels.summary(it) } ?: stringResource(R.string.settings_bc_none),
        onClick = { editing = true },
    )
    if (schedule != null) {
        SwitchRow(
            title = stringResource(R.string.settings_bc_reminder),
            detail = stringResource(R.string.settings_bc_reminder_detail),
            checked = schedule.reminderEnabled,
            onCheckedChange = { on ->
                actions.onContraception(schedule.copy(reminderEnabled = on))
                if (on && !notificationsAllowed) actions.onRequestNotificationPermission()
            },
        )
        if (schedule.reminderEnabled) {
            ValueRow(
                title = stringResource(R.string.settings_bc_reminder_time),
                value = timeText(schedule.reminderTime),
                onClick = { pickingTime = true },
            )
        }
    }
    if (editing) {
        BirthControlDialog(
            initial =
                schedule?.let {
                    BirthControlChoice(it.method, it.activeDays, it.breakDays, it.placebo, it.dayOfPack(today))
                } ?: BirthControlChoice(ContraceptiveMethod.PILL),
            onSave = { choice ->
                actions.onContraception(SettingsViewModel.scheduleFrom(choice, today, schedule))
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    if (pickingTime && schedule != null) {
        TimeDialog(stringResource(R.string.settings_bc_reminder_time), schedule.reminderTime, onDismiss = {
            pickingTime =
                false
        }) {
            actions.onContraception(schedule.copy(reminderTime = it))
            pickingTime = false
        }
    }
}

@Composable
private fun BirthControlDialog(
    initial: BirthControlChoice,
    onSave: (BirthControlChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var method by rememberSaveable { mutableStateOf(initial.method) }
    var active by rememberSaveable { mutableIntStateOf(initial.activeDays) }
    var breakDays by rememberSaveable { mutableIntStateOf(initial.breakDays) }
    var placebo by rememberSaveable { mutableStateOf(initial.placebo) }
    var dayToday by rememberSaveable { mutableIntStateOf(initial.dayToday) }

    // Keep the layout valid as the method changes: patches are changed weekly.
    fun choose(new: ContraceptiveMethod?) {
        method = new
        if (new == ContraceptiveMethod.PATCH) active = if (breakDays > 0) 21 else 28
        if (new != ContraceptiveMethod.PILL) placebo = false
    }
    val packLength = active + breakDays
    // A shorter layout can leave the chosen day past the end of the pack.
    val day = dayToday.coerceIn(1, packLength)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_bc)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm),
            ) {
                Column(Modifier.selectableGroup()) {
                    (ContraceptiveMethod.entries + listOf(null)).forEach { option ->
                        RadioRow(
                            label =
                                option?.let { stringResource(ContraceptionLabels.method(it)) }
                                    ?: stringResource(R.string.settings_bc_other),
                            selected = method == option,
                            onSelect = { choose(option) },
                        )
                    }
                }
                when (method) {
                    null -> {
                        Text(
                            stringResource(R.string.settings_bc_other_detail),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    ContraceptiveMethod.PATCH -> {
                        LabeledSwitch(stringResource(R.string.settings_bc_patch_free_week), breakDays > 0) { on ->
                            breakDays = if (on) 7 else 0
                            active = if (on) 21 else 28
                        }
                    }

                    else -> {
                        val activeLabel =
                            stringResource(
                                if (method ==
                                    ContraceptiveMethod.PILL
                                ) {
                                    R.string.settings_bc_active_pills
                                } else {
                                    R.string.settings_bc_ring_days
                                },
                            )
                        StepperField(activeLabel) {
                            DayCountStepper(
                                active,
                                ContraceptiveSchedule.ACTIVE_DAYS_RANGE,
                                { active = it },
                                activeLabel,
                            )
                        }
                        val breakLabel = stringResource(R.string.settings_bc_break_days)
                        StepperField(breakLabel) {
                            DayCountStepper(
                                breakDays,
                                ContraceptiveSchedule.BREAK_DAYS_RANGE,
                                {
                                    breakDays = it
                                    if (it == 0) placebo = false
                                },
                                breakLabel,
                            )
                        }
                        if (method == ContraceptiveMethod.PILL && breakDays > 0) {
                            LabeledSwitch(stringResource(R.string.settings_bc_placebo), placebo) { placebo = it }
                        }
                    }
                }
                if (method != null) {
                    val dayLabel = stringResource(R.string.settings_bc_day_today)
                    StepperField(dayLabel) {
                        NumberStepper(
                            value = day,
                            range = 1..packLength,
                            onValueChange = { dayToday = it },
                            label = dayLabel,
                            valueText = stringResource(R.string.settings_bc_day_value, day),
                        )
                    }
                    Text(
                        stringResource(
                            if (breakDays >
                                0
                            ) {
                                R.string.settings_bc_break_note
                            } else {
                                R.string.settings_bc_continuous_note
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(BirthControlChoice(method, active, breakDays, placebo, day)) }) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

// --- Medications -----------------------------------------------------------------------

@Composable
internal fun MedicationsSection(
    medications: List<Medication>,
    actions: SettingsActions,
    notificationsAllowed: Boolean,
) {
    // Null: closed; id 0: adding a new one.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    SettingsSection(stringResource(R.string.settings_section_medications)) {
        medications.forEach { medication ->
            SettingRow(title = medication.name, value = medicationSummary(medication), onClick = {
                editingId =
                    medication.id
            })
        }
        SettingRow(
            title = stringResource(R.string.settings_medication_add),
            value = stringResource(R.string.settings_medication_add_detail),
            onClick = { editingId = 0L },
        )
    }
    val id = editingId ?: return
    MedicationDialog(
        initial = medications.firstOrNull { it.id == id },
        onSave = {
            actions.onSaveMedication(it)
            if (it.remindsDaily && !notificationsAllowed) actions.onRequestNotificationPermission()
            editingId = null
        },
        onDelete = {
            actions.onDeleteMedication(id)
            editingId = null
        },
        onDismiss = { editingId = null },
    )
}

@Composable
private fun medicationSummary(medication: Medication): String {
    val reminder = medication.reminderTime
    return when {
        !medication.active -> stringResource(R.string.settings_medication_stopped)
        medication.use == MedicationUse.AS_NEEDED -> stringResource(R.string.settings_medication_as_needed)
        reminder != null -> stringResource(R.string.settings_medication_daily_at, timeText(reminder))
        else -> stringResource(R.string.settings_medication_daily)
    }
}

@Composable
private fun MedicationDialog(
    initial: Medication?,
    onSave: (Medication) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var use by rememberSaveable { mutableStateOf(initial?.use ?: MedicationUse.DAILY) }
    var reminder by rememberSaveable { mutableStateOf(initial?.reminderTime != null) }
    var time by rememberSaveable { mutableStateOf(initial?.reminderTime ?: DEFAULT_MEDICATION_TIME) }
    var active by rememberSaveable { mutableStateOf(initial?.active ?: true) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial ==
                        null
                    ) {
                        R.string.settings_medication_add
                    } else {
                        R.string.settings_medication_edit
                    },
                ),
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Medication.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.settings_medication_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.selectableGroup()) {
                    RadioRow(stringResource(R.string.settings_medication_daily), use == MedicationUse.DAILY) {
                        use = MedicationUse.DAILY
                    }
                    RadioRow(stringResource(R.string.settings_medication_as_needed), use == MedicationUse.AS_NEEDED) {
                        use = MedicationUse.AS_NEEDED
                    }
                }
                if (use == MedicationUse.DAILY) {
                    LabeledSwitch(stringResource(R.string.settings_medication_reminder), reminder) { reminder = it }
                    if (reminder) {
                        TextButton(onClick = { pickingTime = true }) {
                            Text(stringResource(R.string.settings_medication_reminder_at, timeText(time)))
                        }
                    }
                }
                if (initial != null) {
                    LabeledSwitch(stringResource(R.string.settings_medication_active), active) { active = it }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(
                            stringResource(R.string.settings_medication_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val daily = use == MedicationUse.DAILY
                    onSave(Medication(initial?.id ?: 0L, name.trim(), use, time.takeIf { daily && reminder }, active))
                },
            ) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
    if (pickingTime) {
        TimeDialog(stringResource(R.string.settings_medication_reminder), time, onDismiss = { pickingTime = false }) {
            time = it
            pickingTime = false
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.settings_medication_delete_title, name.trim()),
            body = stringResource(R.string.settings_medication_delete_body),
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

// --- Custom tags -----------------------------------------------------------------------

@Composable
internal fun TagsRow(
    tags: List<CustomTag>,
    actions: SettingsActions,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        title = stringResource(R.string.settings_tags),
        value =
            if (tags.isEmpty()) {
                stringResource(R.string.settings_tags_none)
            } else {
                pluralStringResource(R.plurals.settings_tags_count, tags.size, tags.size)
            },
        onClick = { open = true },
    )
    if (open) TagsDialog(tags, actions, onDismiss = { open = false })
}

@Composable
private fun TagsDialog(
    tags: List<CustomTag>,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    // Null: list; 0: new tag; otherwise the tag being edited.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_tags)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.settings_tags_detail), style = MaterialTheme.typography.bodyMedium)
                tags.forEach { tag ->
                    TextButton(onClick = { editingId = tag.id }, modifier = Modifier.fillMaxWidth()) {
                        Text(tag.label, modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = { editingId = 0L }) { Text(stringResource(R.string.settings_tags_new)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
    val id = editingId ?: return
    val tag = tags.firstOrNull { it.id == id }
    TagEditDialog(
        initial = tag,
        onSave = { label ->
            if (tag == null) actions.onCreateTag(label) else actions.onRenameTag(tag.id, label)
            editingId = null
        },
        onDelete = {
            actions.onDeleteTag(id)
            editingId = null
        },
        onDismiss = { editingId = null },
    )
}

@Composable
private fun TagEditDialog(
    initial: CustomTag?,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(initial?.label.orEmpty()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial ==
                        null
                    ) {
                        R.string.settings_tags_new
                    } else {
                        R.string.settings_tags_edit
                    },
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(CustomTag.MAX_LABEL_LENGTH) },
                    label = { Text(stringResource(R.string.settings_tags_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.settings_tags_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = {
                onSave(label.trim())
            }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.settings_tags_delete_title, label.trim()),
            body = stringResource(R.string.settings_tags_delete_body),
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

// --- Shared bits -----------------------------------------------------------------------

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CadenceTheme.spacing.minTouchTarget)
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .padding(vertical = CadenceTheme.spacing.xs),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = CadenceTheme.spacing.sm),
        )
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CadenceTheme.spacing.minTouchTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = CadenceTheme.spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StepperField(
    label: String,
    stepper: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        stepper()
    }
}

private val DEFAULT_MEDICATION_TIME: LocalTime = LocalTime.of(9, 0)
