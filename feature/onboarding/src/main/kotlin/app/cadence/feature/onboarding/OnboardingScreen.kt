package app.cadence.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.model.Goal
import app.cadence.core.ui.components.CycleRing
import app.cadence.core.ui.components.DayCountStepper
import app.cadence.core.ui.components.PrimaryButton
import app.cadence.core.ui.components.RingSegment
import app.cadence.core.ui.theme.CadenceTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import app.cadence.core.ui.R as UiR

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }
    OnboardingContent(
        state = state,
        actions =
            OnboardingActions(
                onNext = viewModel::next,
                onBack = viewModel::back,
                onLastPeriodSelected = viewModel::setLastPeriodStart,
                onSkipLastPeriod = viewModel::skipLastPeriod,
                onCycleLength = viewModel::setCycleLength,
                onTypicalCycleLength = viewModel::useTypicalCycleLength,
                onPeriodLength = viewModel::setPeriodLength,
                onTypicalPeriodLength = viewModel::useTypicalPeriodLength,
                onGoal = viewModel::setGoal,
                onFinish = viewModel::finish,
            ),
        modifier = modifier,
    )
}

/** Everything the onboarding UI can do; lets the content be previewed and tested without a view model. */
class OnboardingActions(
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onLastPeriodSelected: (LocalDate?) -> Unit,
    val onSkipLastPeriod: () -> Unit,
    val onCycleLength: (Int) -> Unit,
    val onTypicalCycleLength: () -> Unit,
    val onPeriodLength: (Int) -> Unit,
    val onTypicalPeriodLength: () -> Unit,
    val onGoal: (Goal) -> Unit,
    val onFinish: () -> Unit,
)

@Composable
fun OnboardingContent(
    state: OnboardingState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    val spacing = CadenceTheme.spacing
    val steps = OnboardingStep.entries
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            if (state.step != OnboardingStep.WELCOME) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = spacing.xs),
                ) {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.onboarding_back))
                    }
                    val progressLabel = stringResource(R.string.onboarding_progress, state.step.ordinal, steps.size - 1)
                    LinearProgressIndicator(
                        progress = { state.step.ordinal / (steps.size - 1f) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = spacing.lg)
                                .semantics { contentDescription = progressLabel },
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.xl, vertical = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                when (state.step) {
                    OnboardingStep.WELCOME -> {
                        WelcomeStep()
                    }

                    OnboardingStep.LAST_PERIOD -> {
                        LastPeriodStep(state, actions.onLastPeriodSelected)
                    }

                    OnboardingStep.CYCLE_LENGTH -> {
                        LengthStep(
                            title = R.string.onboarding_cycle_title,
                            body = R.string.onboarding_cycle_body,
                            label = R.string.onboarding_cycle_label,
                            value = state.cycleLength,
                            range = state.cycleLengthRange,
                            onValueChange = actions.onCycleLength,
                        )
                    }

                    OnboardingStep.PERIOD_LENGTH -> {
                        LengthStep(
                            title = R.string.onboarding_period_title,
                            body = R.string.onboarding_period_body,
                            label = R.string.onboarding_period_label,
                            value = state.periodLength,
                            range = state.periodLengthRange,
                            onValueChange = actions.onPeriodLength,
                        )
                    }

                    OnboardingStep.GOAL -> {
                        GoalStep(state.goal, actions.onGoal)
                    }
                }
            }
            BottomActions(state, actions)
        }
    }
}

@Composable
private fun BottomActions(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    val spacing = CadenceTheme.spacing
    Column(
        Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        val modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        when (state.step) {
            OnboardingStep.WELCOME -> {
                PrimaryButton(stringResource(R.string.onboarding_get_started), actions.onNext, modifier)
            }

            OnboardingStep.LAST_PERIOD -> {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_next),
                    onClick = actions.onNext,
                    modifier = modifier,
                    enabled = state.lastPeriodStart != null,
                )
                TextButton(
                    onClick = actions.onSkipLastPeriod,
                ) { Text(stringResource(R.string.onboarding_last_period_skip)) }
            }

            OnboardingStep.CYCLE_LENGTH -> {
                PrimaryButton(stringResource(R.string.onboarding_next), actions.onNext, modifier)
                TextButton(
                    onClick = actions.onTypicalCycleLength,
                ) { Text(stringResource(R.string.onboarding_not_sure)) }
            }

            OnboardingStep.PERIOD_LENGTH -> {
                PrimaryButton(stringResource(R.string.onboarding_next), actions.onNext, modifier)
                TextButton(
                    onClick = actions.onTypicalPeriodLength,
                ) { Text(stringResource(R.string.onboarding_not_sure)) }
            }

            OnboardingStep.GOAL -> {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_finish),
                    onClick = actions.onFinish,
                    modifier = modifier,
                    enabled = !state.finishing,
                )
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun StepBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.lg),
        content = content,
    )
}

@Composable
private fun WelcomeStep(modifier: Modifier = Modifier) {
    StepColumn(modifier) {
        Spacer(Modifier.height(CadenceTheme.spacing.xxl))
        CycleRing(
            cycleLength = 28,
            today = 9,
            segments = listOf(RingSegment(1, 9, MaterialTheme.colorScheme.primary)),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        StepTitle(stringResource(R.string.onboarding_welcome_title))
        StepBody(stringResource(R.string.onboarding_welcome_body))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(CadenceTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.xs),
            ) {
                Text(
                    stringResource(R.string.onboarding_welcome_private_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.onboarding_welcome_private_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            stringResource(R.string.onboarding_welcome_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LastPeriodStep(
    state: OnboardingState,
    onDateSelect: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDateSelect by rememberUpdatedState(onDateSelect)
    val earliest = state.earliestLastPeriod.toUtcMillis()
    val latest = state.today.toUtcMillis()
    val years = state.earliestLastPeriod.year..state.today.year
    val selectable =
        remember(earliest, latest) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in earliest..latest

                override fun isSelectableYear(year: Int) = year in years
            }
        }
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = state.lastPeriodStart?.toUtcMillis(),
            initialDisplayedMonthMillis = latest,
            yearRange = years,
            selectableDates = selectable,
        )
    LaunchedEffect(pickerState.selectedDateMillis) {
        currentOnDateSelect(
            pickerState.selectedDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            },
        )
    }
    StepColumn(modifier) {
        StepTitle(stringResource(R.string.onboarding_last_period_title))
        StepBody(stringResource(R.string.onboarding_last_period_body))
        DatePicker(state = pickerState, showModeToggle = false, title = null, headline = null)
    }
}

/** Material's date picker works in UTC milliseconds at midnight. */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun LengthStep(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes label: Int,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    StepColumn(modifier) {
        StepTitle(stringResource(title))
        StepBody(stringResource(body))
        Box(Modifier.fillMaxWidth().padding(top = CadenceTheme.spacing.lg), contentAlignment = Alignment.Center) {
            DayCountStepper(value = value, range = range, onValueChange = onValueChange, label = stringResource(label))
        }
    }
}

@Composable
private fun GoalStep(
    selected: Goal,
    onSelect: (Goal) -> Unit,
    modifier: Modifier = Modifier,
) {
    StepColumn(modifier) {
        StepTitle(stringResource(R.string.onboarding_goal_title))
        StepBody(stringResource(R.string.onboarding_goal_body))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md)) {
            GoalOption(
                Goal.TRACK,
                R.string.onboarding_goal_track,
                R.string.onboarding_goal_track_detail,
                selected,
                onSelect,
            )
            GoalOption(
                Goal.BIRTH_CONTROL,
                R.string.onboarding_goal_birth_control,
                R.string.onboarding_goal_birth_control_detail,
                selected,
                onSelect,
            )
        }
    }
}

@Composable
private fun GoalOption(
    goal: Goal,
    @StringRes title: Int,
    @StringRes detail: Int,
    selected: Goal,
    onSelect: (Goal) -> Unit,
) {
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .selectable(selected = goal == selected, onClick = { onSelect(goal) }, role = Role.RadioButton),
    ) {
        Row(
            Modifier.padding(CadenceTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
        ) {
            RadioButton(selected = goal == selected, onClick = null)
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
