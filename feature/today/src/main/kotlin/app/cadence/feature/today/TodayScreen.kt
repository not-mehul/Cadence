package app.cadence.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.cycles.CycleStatus
import app.cadence.core.domain.cycles.FertileWindow
import app.cadence.core.domain.model.CyclePhase
import app.cadence.core.domain.model.Goal
import app.cadence.core.ui.components.CadenceCard
import app.cadence.core.ui.components.CardHeader
import app.cadence.core.ui.components.HeroCard
import app.cadence.core.ui.components.PhaseChip
import app.cadence.core.ui.components.PhaseStyle
import app.cadence.core.ui.components.PhaseStyles
import app.cadence.core.ui.components.PrimaryButton
import app.cadence.core.ui.expressive.Buddy
import app.cadence.core.ui.expressive.BuddyFace
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.formatHeading
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate
import java.time.LocalTime
import app.cadence.core.ui.R as UiR

@Composable
fun TodayScreen(
    onOpenLog: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val loggedMessage by rememberUpdatedState(stringResource(R.string.today_logged))
    val undoLabel by rememberUpdatedState(stringResource(R.string.today_undo))

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TodayEvent.PeriodLogged -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = loggedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoLogged(event.days)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The app shell already handles the status and navigation bars.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when (val s = state) {
            TodayUiState.Loading -> {
                Box(Modifier.fillMaxSize())
            }

            is TodayUiState.Ready -> {
                TodayContent(
                    state = s,
                    onStartPeriod = viewModel::startPeriod,
                    onEndPeriod = viewModel::endPeriod,
                    onLogToday = viewModel::logToday,
                    onOpenLog = { onOpenLog(s.snapshot.today) },
                    modifier = Modifier.padding(padding),
                    onRecordDose = viewModel::recordDose,
                    onMedicationTakenChange = viewModel::setMedicationTaken,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

/** Stateless Today content, previewable and testable without a view model. */
@Composable
fun TodayContent(
    state: TodayUiState.Ready,
    onStartPeriod: () -> Unit,
    onEndPeriod: (lastDay: LocalDate) -> Unit,
    onLogToday: () -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
    onRecordDose: (LocalDate, DoseStatus?) -> Unit = { _, _ -> },
    onMedicationTakenChange: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
) {
    val snapshot = state.snapshot
    val birthControl = state.birthControl
    val pack = snapshot.pack
    val spacing = CadenceTheme.spacing
    var showEndDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenGutter, vertical = spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        val style = heroStyle(snapshot, birthControl != null)
        TodayHeader(snapshot.today, style)

        // The hero takes on the colour of where you are in your cycle (or pack).
        HeroCard(style) {
            heroLabel(snapshot, birthControl)?.let { PhaseChip(it, style) }
            val ringModifier = Modifier.widthIn(max = 248.dp).fillMaxWidth()
            when {
                birthControl != null && pack != null -> {
                    PackRing(birthControl.schedule, pack, ringModifier)
                }

                snapshot.timeline != null -> {
                    TodayRing(snapshot, ringModifier)
                }

                // Nothing logged yet: a cheerful buddy stands in for the ring.
                else -> {
                    Buddy(
                        body = buddyBody(style),
                        face = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialShapes.Cookie9Sided,
                        expression = BuddyFace.JOYFUL,
                        size = 148.dp,
                    )
                }
            }
            Text(
                text = headline(snapshot.status),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (pack != null) PackSupportingText(pack) else SupportingText(snapshot)
        }

        if (snapshot.status is CycleStatus.OnPeriod) {
            PrimaryButton(
                stringResource(R.string.today_action_end),
                onClick = { showEndDialog = true },
                Modifier.fillMaxWidth(),
                leadingIcon = painterResource(UiR.drawable.ms_check),
            )
        } else {
            PrimaryButton(
                stringResource(R.string.today_action_start),
                onClick = onStartPeriod,
                Modifier.fillMaxWidth(),
                leadingIcon = painterResource(UiR.drawable.ms_water_drop),
            )
        }
        if (snapshot.canExtendPeriod) {
            TextButton(onClick = onLogToday, shapes = ButtonDefaults.shapes()) {
                Icon(
                    painterResource(UiR.drawable.ms_add),
                    contentDescription = null,
                    modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                )
                Text(stringResource(R.string.today_action_extend))
            }
        }

        if (birthControl != null) {
            DoseCard(birthControl, snapshot.today, onRecordDose)
            if (birthControl.missed.isNotEmpty()) MissedDosesCard(birthControl.missed, onRecordDose)
        }
        if (state.goal == Goal.BIRTH_CONTROL && birthControl == null) SetUpBirthControlCard(onOpenSettings)
        if (state.medications.isNotEmpty()) MedicationsCard(state.medications, onMedicationTakenChange)
        TodayLogCard(entry = state.todayEntry, onOpen = onOpenLog, tagLabels = state.tagLabels)
        snapshot.fertileWindow?.let { FertileWindowCard(it) }
        PhaseCard(snapshot = snapshot, goal = state.goal)
    }

    val period = snapshot.currentPeriod
    if (showEndDialog && period != null) {
        EndPeriodDialog(
            canEndYesterday = period.start.isBefore(snapshot.today),
            onEnd = { endedYesterday ->
                showEndDialog = false
                onEndPeriod(if (endedYesterday) snapshot.today.minusDays(1) else snapshot.today)
            },
            onDismiss = { showEndDialog = false },
        )
    }
}

@Composable
private fun SupportingText(snapshot: app.cadence.core.domain.cycles.CycleSnapshot) {
    val prediction = snapshot.prediction
    val dateFormat = rememberDateFormatter(DateSkeletons.DAY_MONTH_SHORT)
    val lines =
        when {
            prediction == null -> {
                listOf(stringResource(R.string.today_no_data_body))
            }

            snapshot.status is CycleStatus.Late || snapshot.status is CycleStatus.Due -> {
                listOf(
                    stringResource(R.string.today_was_expected, dateFormat.format(prediction.expectedNextStart)),
                    basisText(prediction),
                )
            }

            else -> {
                listOf(
                    stringResource(R.string.today_next_expected, dateFormat.format(prediction.expectedNextStart)),
                    stringResource(
                        R.string.today_likely_range,
                        dateFormat.format(prediction.earliestNextStart),
                        dateFormat.format(prediction.latestNextStart),
                    ),
                    basisText(prediction),
                )
            }
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.xs),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The estimated fertile window, kept apart from the period prediction so neither crowds the other. */
@Composable
private fun FertileWindowCard(fertile: FertileWindow) {
    val dateFormat = rememberDateFormatter(DateSkeletons.DAY_MONTH_SHORT)
    val style = PhaseStyles.of(CyclePhase.OVULATORY)
    CadenceCard {
        CardHeader(
            stringResource(R.string.today_fertile_title),
            icon = painterResource(style.icon),
            accent = style.color,
            accentContainer = style.tint,
        )
        Text(
            text =
                stringResource(
                    R.string.today_fertile_window,
                    dateFormat.format(fertile.window.start),
                    dateFormat.format(fertile.window.end),
                ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.today_fertile_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CadenceTheme.spacing.xs),
        )
    }
}

/** A greeting for the time of day and the date, with a little buddy in the colours of the phase. */
@Composable
private fun TodayHeader(
    today: LocalDate,
    style: PhaseStyle,
) {
    val greeting =
        remember {
            when (LocalTime.now().hour) {
                in MORNING_HOURS -> R.string.today_greeting_morning
                in AFTERNOON_HOURS -> R.string.today_greeting_afternoon
                else -> R.string.today_greeting_evening
            }
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(greeting),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = rememberDateFormatter(DateSkeletons.WEEKDAY_DAY_MONTH).formatHeading(today),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Buddy(
            body = buddyBody(style),
            face = MaterialTheme.colorScheme.onSurface,
            shape = style.shape,
            size = 56.dp,
        )
    }
}

/** The buddy wears a slightly deeper version of the phase's wash, so it shows on any background. */
private fun buddyBody(style: PhaseStyle) = lerp(style.tint, style.color, BUDDY_DEPTH)

private const val BUDDY_DEPTH = 0.22f

private val MORNING_HOURS = 5..11
private val AFTERNOON_HOURS = 12..17

@Composable
private fun PhaseCard(
    snapshot: app.cadence.core.domain.cycles.CycleSnapshot,
    goal: app.cadence.core.domain.model.Goal,
) {
    val (title, body) = phaseCopy(snapshot, goal)
    val details = phaseDetails(title)
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val pack = snapshot.pack
    val style =
        when {
            pack != null -> {
                PhaseStyles.pack(pack.inBreak)
            }

            snapshot.phase != null -> {
                PhaseStyles.of(snapshot.phase)
            }

            snapshot.status is CycleStatus.Late || snapshot.status is CycleStatus.Due -> {
                PhaseStyles.neutral(
                    UiR.drawable.ic_clock,
                )
            }

            else -> {
                PhaseStyles.neutral(UiR.drawable.ic_pattern)
            }
        }
    CadenceCard {
        CardHeader(
            stringResource(title),
            icon = painterResource(style.icon),
            accent = style.color,
            accentContainer = style.tint,
        )
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        if (details != null) {
            if (expanded) {
                Spacer(Modifier.height(CadenceTheme.spacing.sm))
                Text(stringResource(details.first), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(CadenceTheme.spacing.xs))
                Text(
                    stringResource(details.second),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.phase_show_less else R.string.phase_learn_more))
            }
        }
    }
}
