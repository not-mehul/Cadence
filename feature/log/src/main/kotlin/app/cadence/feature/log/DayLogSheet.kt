package app.cadence.feature.log

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCatalog
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.log.SexActivity
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.ui.components.ScaleSelector
import app.cadence.core.ui.components.SectionHeader
import app.cadence.core.ui.expressive.FlowIcon
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.formatHeading
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.log.LogIcons
import app.cadence.core.ui.log.LogLabels
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate
import app.cadence.core.ui.R as UiR

/** The daily log for [date], as a bottom sheet over the current screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogSheet(
    date: LocalDate,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayLogViewModel = hiltViewModel(key = "day-log"),
) {
    LaunchedEffect(date) { viewModel.load(date) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val close = {
        viewModel.flush()
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = close,
        // Full height straight away: there's a lot to log.
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        modifier = modifier,
    ) {
        when (val s = state) {
            DayLogUiState.Loading -> {
                Box(Modifier.fillMaxWidth().heightIn(min = 240.dp))
            }

            is DayLogUiState.Ready -> {
                DayLogContent(
                    state = s,
                    onEdit = viewModel::edit,
                    onNoteChange = viewModel::editNote,
                    onDone = close,
                    tracking =
                        DayLogTrackingActions(
                            onDose = viewModel::recordDose,
                            onMedicationTaken = viewModel::setMedicationTaken,
                            onCreateTag = viewModel::createTag,
                        ),
                )
            }
        }
    }
}

/** Stateless log content; previewable and testable without a view model. */
@Composable
fun DayLogContent(
    state: DayLogUiState.Ready,
    onEdit: ((DayEntry) -> DayEntry) -> Unit,
    onNoteChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    tracking: DayLogTrackingActions = DayLogTrackingActions(),
) {
    val entry = state.entry
    val spacing = CadenceTheme.spacing
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenGutter)
            .padding(bottom = spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Header(entry.date, state.isToday, onDone)

        Section(LogCategory.FLOW, state) { FlowPicker(entry.flow) { flow -> onEdit { it.copy(flow = flow) } } }
        state.dose?.let { DoseSection(it, state.doseStatus, state.isToday, tracking.onDose) }
        Section(LogCategory.SYMPTOMS, state, hint = stringResource(R.string.log_symptoms_hint)) {
            SymptomChips(entry) { id -> onEdit { it.cycleSymptom(id) } }
        }
        Section(LogCategory.MOOD, state) { MoodChips(entry) { id -> onEdit { it.toggleMood(id) } } }
        Section(LogCategory.TAGS, state) {
            TagChips(entry, state.tags, onToggle = { id ->
                onEdit { it.toggleTag(id) }
            }, onCreate = tracking.onCreateTag)
        }
        Section(LogCategory.PAIN, state) {
            ScaleSelector(
                range = DayEntry.PAIN_RANGE,
                selected = entry.painLevel,
                onSelect = { value -> onEdit { it.copy(painLevel = value) } },
                label = stringResource(UiR.string.category_pain),
                lowLabel = stringResource(R.string.log_pain_low),
                highLabel = stringResource(R.string.log_pain_high),
            )
        }
        RatingSection(
            LogCategory.ENERGY,
            state,
            entry.energy,
            R.string.log_rating_low,
            R.string.log_rating_high,
        ) { value ->
            onEdit { it.copy(energy = value) }
        }
        RatingSection(LogCategory.SLEEP, state, entry.sleep, R.string.log_sleep_low, R.string.log_sleep_high) { value ->
            onEdit { it.copy(sleep = value) }
        }
        RatingSection(
            LogCategory.STRESS,
            state,
            entry.stress,
            R.string.log_rating_low,
            R.string.log_rating_high,
        ) { value ->
            onEdit { it.copy(stress = value) }
        }
        if (state.medications.isNotEmpty()) {
            Section(LogCategory.MEDICATIONS, state) { MedicationChips(state.medications, tracking.onMedicationTaken) }
        }
        Section(LogCategory.SEX, state) { SexPicker(entry.sex) { sex -> onEdit { it.copy(sex = sex) } } }
        Section(LogCategory.NOTES, state) { NoteField(entry.note.orEmpty(), onNoteChange) }
    }
}

@Composable
private fun Header(
    date: LocalDate,
    isToday: Boolean,
    onDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text =
                    if (isToday) {
                        stringResource(
                            R.string.log_today,
                        )
                    } else {
                        rememberDateFormatter(DateSkeletons.WEEKDAY_DAY_MONTH).formatHeading(date)
                    },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.log_autosave),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onDone, shapes = ButtonDefaults.shapes()) {
            Icon(
                painterResource(UiR.drawable.ms_check),
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
            )
            Text(stringResource(R.string.log_done))
        }
    }
}

@Composable
internal fun Section(
    category: LogCategory,
    state: DayLogUiState.Ready,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    if (!state.shows(category)) return
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = CadenceTheme.spacing.xs))
        SectionHeader(stringResource(LogLabels.category(category)), icon = LogIcons.category(category))
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = CadenceTheme.spacing.sm),
            )
        }
        content()
    }
}

@Composable
private fun RatingSection(
    category: LogCategory,
    state: DayLogUiState.Ready,
    value: Int?,
    low: Int,
    high: Int,
    onSelect: (Int?) -> Unit,
) {
    Section(category, state) {
        ScaleSelector(
            range = DayEntry.RATING_RANGE,
            selected = value,
            onSelect = onSelect,
            label = stringResource(LogLabels.category(category)),
            lowLabel = stringResource(low),
            highLabel = stringResource(high),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.xs),
    ) { content() }
}

@Composable
private fun FlowPicker(
    selected: MenstrualFlow?,
    onSelect: (MenstrualFlow?) -> Unit,
) {
    ChipRow {
        (listOf(null) + MenstrualFlow.entries).forEach { flow ->
            FilterChip(
                selected = flow == selected,
                onClick = { onSelect(flow) },
                label = { Text(stringResource(LogLabels.flow(flow))) },
                leadingIcon = { FlowIcon(flow, size = FilterChipDefaults.IconSize) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

@Composable
private fun SymptomChips(
    entry: DayEntry,
    onTap: (String) -> Unit,
) {
    val offState = stringResource(R.string.log_symptom_state_off)
    ChipRow {
        LogCatalog.symptoms.forEach { id ->
            val severity = entry.symptoms[id]
            val name = LogLabels.symptom(id)
            val severityText = severity?.let { stringResource(LogLabels.severity(it)) }
            FilterChip(
                selected = severity != null,
                onClick = { onTap(id) },
                label = { Text(if (severityText == null) name else "$name · $severityText") },
                leadingIcon = { ChipIcon(LogIcons.symptom(id)) },
                modifier = Modifier.semantics { stateDescription = severityText ?: offState },
            )
        }
    }
}

@Composable
private fun MoodChips(
    entry: DayEntry,
    onTap: (String) -> Unit,
) {
    ChipRow {
        LogCatalog.moods.forEach { id ->
            FilterChip(
                selected = id in entry.moods,
                onClick = { onTap(id) },
                label = { Text(LogLabels.mood(id)) },
                leadingIcon = { ChipIcon(LogIcons.mood(id)) },
                modifier = Modifier.semantics { role = Role.Checkbox },
            )
        }
    }
}

@Composable
private fun SexPicker(
    selected: SexActivity?,
    onSelect: (SexActivity?) -> Unit,
) {
    ChipRow {
        SexActivity.entries.forEach { sex ->
            FilterChip(
                selected = sex == selected,
                // Tapping the selected option again clears it.
                onClick = { onSelect(if (sex == selected) null else sex) },
                label = { Text(stringResource(LogLabels.sex(sex))) },
                leadingIcon = { ChipIcon(LogIcons.sex(sex)) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

@Composable
private fun NoteField(
    text: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.log_note_placeholder)) },
        supportingText = {
            if (text.length > DayEntry.MAX_NOTE_LENGTH * 3 / 4) {
                Text(stringResource(R.string.log_note_count, text.length, DayEntry.MAX_NOTE_LENGTH))
            }
        },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A decorative icon at the start of a chip; the chip's label carries the meaning. */
@Composable
internal fun ChipIcon(
    @DrawableRes icon: Int,
) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}
