package app.cadence.feature.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.ui.components.SectionHeader
import app.cadence.core.ui.contraception.ContraceptionLabels
import app.cadence.core.ui.theme.CadenceTheme
import app.cadence.core.ui.R as UiR

/** What the Phase 6 parts of the log can do. */
class DayLogTrackingActions(
    val onDose: (DoseStatus?) -> Unit = {},
    val onMedicationTaken: (Long, Boolean) -> Unit = { _, _ -> },
    val onCreateTag: (String) -> Unit = {},
)

/** The day's pill, patch or ring: taken, missed, or not recorded. */
@Composable
internal fun DoseSection(
    action: DoseAction,
    status: DoseStatus?,
    isToday: Boolean,
    onDose: (DoseStatus?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = CadenceTheme.spacing.xs))
        SectionHeader(stringResource(R.string.log_birth_control), icon = UiR.drawable.ms_pill)
        Text(
            stringResource(if (isToday) ContraceptionLabels.task(action) else ContraceptionLabels.noun(action)),
            modifier = Modifier.padding(bottom = CadenceTheme.spacing.sm),
        )
        ChipRow {
            val options =
                listOf(
                    Triple(DoseStatus.TAKEN, R.string.log_dose_taken, UiR.drawable.ms_check),
                    Triple(DoseStatus.MISSED, R.string.log_dose_missed, UiR.drawable.ms_close),
                )
            options.forEach { (option, label, icon) ->
                FilterChip(
                    selected = status == option,
                    // Tapping the selected option again clears it.
                    onClick = { onDose(if (status == option) null else option) },
                    label = { Text(stringResource(label)) },
                    leadingIcon = { ChipIcon(icon) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
        }
    }
}

@Composable
internal fun TagChips(
    entry: DayEntry,
    tags: List<CustomTag>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    ChipRow {
        tags.forEach { tag ->
            FilterChip(
                selected = tag.id in entry.tags,
                onClick = { onToggle(tag.id) },
                label = { Text(tag.label) },
                leadingIcon = { ChipIcon(UiR.drawable.ms_sell) },
                modifier = Modifier.semantics { role = Role.Checkbox },
            )
        }
        AssistChip(
            onClick = { adding = true },
            label = { Text(stringResource(R.string.log_tag_new)) },
            leadingIcon = { ChipIcon(UiR.drawable.ms_add) },
        )
    }
    if (adding) {
        NewTagDialog(
            onCreate = {
                onCreate(it)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun NewTagDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_tag_new_title)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(CustomTag.MAX_LABEL_LENGTH) },
                label = { Text(stringResource(R.string.log_tag_label)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { if (label.isNotBlank()) onCreate(label.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = {
                onCreate(label.trim())
            }) { Text(stringResource(R.string.log_tag_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_cancel)) } },
    )
}

@Composable
internal fun MedicationChips(
    medications: List<LoggedMedication>,
    onTakenChange: (Long, Boolean) -> Unit,
) {
    ChipRow {
        medications.forEach { (medication, taken) ->
            FilterChip(
                selected = taken,
                onClick = { onTakenChange(medication.id, !taken) },
                label = { Text(medication.name) },
                leadingIcon = { ChipIcon(UiR.drawable.ms_medication) },
                modifier = Modifier.semantics { role = Role.Checkbox },
            )
        }
    }
}
