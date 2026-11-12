package app.cadence.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.ui.components.CadenceSurface
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.formatHeading
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.theme.CadenceTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import app.cadence.core.ui.R as UiR

/** Everything the calendar can ask for. */
data class CalendarActions(
    /** Shows a day's details under the month. */
    val onSelect: (LocalDate) -> Unit = {},
    /** "Edit period days" mode: adds or removes one period day. */
    val onToggle: (LocalDate) -> Unit = {},
    val onOpenLog: (LocalDate) -> Unit = {},
    /** A period started on this day: fills in the expected length. */
    val onStartPeriod: (LocalDate) -> Unit = {},
    /** The period ended on this day: clears the days after it. */
    val onEndPeriod: (LocalDate) -> Unit = {},
    val onRemovePeriodDay: (LocalDate) -> Unit = {},
)

@Composable
fun CalendarScreen(
    onOpenLog: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val formatter = rememberDateFormatter(DateSkeletons.DAY_MONTH_SHORT)
    val loggedTemplate by rememberUpdatedState(stringResource(R.string.calendar_period_logged))
    val undoLabel by rememberUpdatedState(stringResource(R.string.calendar_undo))

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CalendarEvent.PeriodLogged -> {
                    val message =
                        loggedTemplate.format(
                            formatter.format(event.days.min()),
                            formatter.format(event.days.max()),
                        )
                    val result = snackbarHostState.showSnackbar(message, undoLabel, duration = SnackbarDuration.Long)
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
            CalendarUiState.Loading -> {
                Box(Modifier.fillMaxSize())
            }

            is CalendarUiState.Ready -> {
                CalendarContent(
                    s,
                    CalendarActions(
                        onSelect = viewModel::select,
                        onToggle = viewModel::toggle,
                        onOpenLog = onOpenLog,
                        onStartPeriod = viewModel::startPeriodOn,
                        onEndPeriod = viewModel::endPeriodOn,
                        onRemovePeriodDay = viewModel::removePeriodDay,
                    ),
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

/** Months either side of today the pager can reach. */
private const val MONTH_RANGE = 120

/** Narrower than the usual gutter: seven 48dp days need 336dp, and the smallest phones are 360dp wide. */
private val CALENDAR_GUTTER = 8.dp

private val MAX_CONTENT_WIDTH = 480.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarContent(
    state: CalendarUiState.Ready,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    var editPeriods by rememberSaveable { mutableStateOf(false) }
    val currentMonth = YearMonth.from(state.today)
    val pagerState = rememberPagerState(initialPage = MONTH_RANGE) { MONTH_RANGE * 2 + 1 }
    val scope = rememberCoroutineScope()
    val visibleMonth = currentMonth.plusMonths((pagerState.currentPage - MONTH_RANGE).toLong())
    val spacing = CadenceTheme.spacing
    val goToPage: (Int) -> Unit = { page -> scope.launch { pagerState.animateScrollToPage(page) } }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CALENDAR_GUTTER, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The month sits in its own card, nearly edge to edge, so every day is a full-size touch target.
        CadenceSurface(Modifier.widthIn(max = MAX_CONTENT_WIDTH)) {
            Column(Modifier.padding(vertical = spacing.md, horizontal = spacing.xs)) {
                MonthHeader(
                    month = visibleMonth,
                    onPrevious = { goToPage(pagerState.currentPage - 1) },
                    onNext = { goToPage(pagerState.currentPage + 1) },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val month = currentMonth.plusMonths((page - MONTH_RANGE).toLong())
                    MonthGrid(
                        month = month,
                        marks = state.marksFor(month),
                        state = state,
                        editPeriods = editPeriods,
                        actions = actions,
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth(),
        ) {
            val onToday = visibleMonth == currentMonth && state.selected == state.today
            if (!onToday) {
                FilledTonalButton(onClick = {
                    goToPage(MONTH_RANGE)
                    actions.onSelect(state.today)
                }) {
                    Icon(painterResource(UiR.drawable.ms_today), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.calendar_jump_today), Modifier.padding(start = spacing.sm))
                }
            }
            FilterChip(
                selected = editPeriods,
                onClick = { editPeriods = !editPeriods },
                label = { Text(stringResource(R.string.calendar_edit_periods)) },
                leadingIcon = {
                    Icon(
                        painterResource(if (editPeriods) UiR.drawable.ms_check else UiR.drawable.ms_edit_calendar),
                        null,
                        Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }

        if (editPeriods) {
            Text(
                stringResource(R.string.calendar_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            DayDetails(state, actions, Modifier.widthIn(max = MAX_CONTENT_WIDTH))
        }

        Legend(
            showFertileWindow = state.showsFertileWindow,
            modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(horizontal = spacing.sm),
        )
        if (state.showsFertileWindow) {
            Text(
                stringResource(R.string.calendar_fertile_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = spacing.sm),
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val colors =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CadenceTheme.spacing.sm),
    ) {
        FilledTonalIconButton(onClick = onPrevious, colors = colors, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(UiR.drawable.ms_chevron_left), stringResource(R.string.calendar_previous_month))
        }
        val formatter = rememberDateFormatter(DateSkeletons.MONTH_YEAR)
        // The title slides the way the months move.
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val direction = if (targetState.isAfter(initialState)) 1 else -1
                (slideInHorizontally { it / 3 * direction } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 3 * direction } + fadeOut())
            },
            contentAlignment = Alignment.Center,
            label = "month",
            modifier = Modifier.weight(1f),
        ) { shown ->
            Text(
                text = formatter.formatHeading(shown),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
        }
        FilledTonalIconButton(onClick = onNext, colors = colors, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(UiR.drawable.ms_chevron_right), stringResource(R.string.calendar_next_month))
        }
    }
}

/** The day's date as a heading, e.g. "Friday, 20 March". */
@Composable
internal fun dayHeading(date: LocalDate): String =
    rememberDateFormatter(DateSkeletons.WEEKDAY_DAY_MONTH).formatHeading(date)
