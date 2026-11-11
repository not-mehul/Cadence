package app.cadence.feature.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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

@Composable
fun CalendarScreen(
    onOpenLog: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        CalendarUiState.Loading -> {
            Box(modifier.fillMaxSize())
        }

        is CalendarUiState.Ready -> {
            CalendarContent(
                s,
                onToggle = viewModel::toggle,
                onOpenLog = onOpenLog,
                modifier = modifier,
            )
        }
    }
}

/** Months either side of today the pager can reach. */
private const val MONTH_RANGE = 120

/** Narrower than the usual gutter: seven 48dp days need 336dp, and the smallest phones are 360dp wide. */
private val CALENDAR_GUTTER = 8.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarContent(
    state: CalendarUiState.Ready,
    onToggle: (LocalDate) -> Unit,
    onOpenLog: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editPeriods by rememberSaveable { mutableStateOf(false) }
    val currentMonth = YearMonth.from(state.today)
    val pagerState = rememberPagerState(initialPage = MONTH_RANGE) { MONTH_RANGE * 2 + 1 }
    val scope = rememberCoroutineScope()
    val visibleMonth = currentMonth.plusMonths((pagerState.currentPage - MONTH_RANGE).toLong())
    val spacing = CadenceTheme.spacing

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
        CadenceSurface(Modifier.widthIn(max = 480.dp)) {
            Column(Modifier.padding(vertical = spacing.sm, horizontal = spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    ) {
                        Icon(
                            painterResource(UiR.drawable.ic_chevron_left),
                            stringResource(R.string.calendar_previous_month),
                        )
                    }
                    Text(
                        text = rememberDateFormatter(DateSkeletons.MONTH_YEAR).formatHeading(visibleMonth),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    ) {
                        Icon(
                            painterResource(UiR.drawable.ic_chevron_right),
                            stringResource(R.string.calendar_next_month),
                        )
                    }
                }

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
                        today = state.today,
                        state = state,
                        editPeriods = editPeriods,
                        onToggle = onToggle,
                        onOpenLog = onOpenLog,
                    )
                }
            }
        }

        if (visibleMonth != currentMonth) {
            TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(MONTH_RANGE) } }) {
                Text(stringResource(R.string.calendar_jump_today))
            }
        }
        Legend(
            showFertileWindow = state.showsFertileWindow,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = spacing.sm),
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
        FilterChip(
            selected = editPeriods,
            onClick = { editPeriods = !editPeriods },
            label = { Text(stringResource(R.string.calendar_edit_periods)) },
        )
        Text(
            stringResource(if (editPeriods) R.string.calendar_hint else R.string.calendar_hint_log),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
