package app.cadence.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.cycles.CycleStatus
import app.cadence.core.ui.components.CadenceCard
import app.cadence.core.ui.components.PrimaryButton
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate

@Composable
fun TodayScreen(
    onOpenLog: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
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
) {
    val snapshot = state.snapshot
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
        Text(
            text = rememberDateFormatter(DateSkeletons.WEEKDAY_DAY_MONTH).format(snapshot.today),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TodayRing(snapshot, Modifier.widthIn(max = 280.dp).fillMaxWidth())

        Text(
            text = headline(snapshot.status),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        SupportingText(snapshot)

        Spacer(Modifier.height(spacing.xs))
        if (snapshot.status is CycleStatus.OnPeriod) {
            PrimaryButton(
                stringResource(R.string.today_action_end),
                onClick = { showEndDialog = true },
                Modifier.fillMaxWidth(),
            )
        } else {
            PrimaryButton(stringResource(R.string.today_action_start), onClick = onStartPeriod, Modifier.fillMaxWidth())
        }
        if (snapshot.canExtendPeriod) {
            TextButton(onClick = onLogToday) { Text(stringResource(R.string.today_action_extend)) }
        }

        Spacer(Modifier.height(spacing.xs))
        TodayLogCard(entry = state.todayEntry, onOpen = onOpenLog)
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

            else -> {
                listOf(
                    if (snapshot.status is CycleStatus.Late) {
                        stringResource(R.string.today_was_expected, dateFormat.format(prediction.expectedNextStart))
                    } else {
                        stringResource(R.string.today_next_expected, dateFormat.format(prediction.nextStart))
                    },
                    basisText(prediction),
                )
            }
        }
    lines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CadenceCardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PhaseCard(
    snapshot: app.cadence.core.domain.cycles.CycleSnapshot,
    goal: app.cadence.core.domain.model.Goal,
) {
    val (title, body) = phaseCopy(snapshot, goal)
    CadenceCard {
        CadenceCardTitle(stringResource(title))
        Spacer(Modifier.height(CadenceTheme.spacing.xs))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}
