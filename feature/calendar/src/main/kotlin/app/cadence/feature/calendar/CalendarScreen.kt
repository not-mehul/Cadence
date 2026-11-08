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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.theme.CadenceTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import app.cadence.core.ui.R as UiR

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        CalendarUiState.Loading -> Box(modifier.fillMaxSize())
        is CalendarUiState.Ready -> CalendarContent(s, onToggle = viewModel::toggle, modifier = modifier)
    }
}

/** Months either side of today the pager can reach. */
private const val MONTH_RANGE = 120

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarContent(
    state: CalendarUiState.Ready,
    onToggle: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                .padding(horizontal = spacing.screenGutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                Icon(painterResource(UiR.drawable.ic_chevron_left), stringResource(R.string.calendar_previous_month))
            }
            Text(
                text = rememberDateFormatter(DateSkeletons.MONTH_YEAR).format(visibleMonth),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                Icon(painterResource(UiR.drawable.ic_chevron_right), stringResource(R.string.calendar_next_month))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 1,
        ) { page ->
            val month = currentMonth.plusMonths((page - MONTH_RANGE).toLong())
            MonthGrid(
                month = month,
                marks = state.marksFor(month),
                today = state.today,
                isLogged = state::isLogged,
                canToggle = state::canToggle,
                onToggle = onToggle,
            )
        }

        if (visibleMonth != currentMonth) {
            TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(MONTH_RANGE) } }) {
                Text(stringResource(R.string.calendar_jump_today))
            }
        }
        Legend(Modifier.widthIn(max = 480.dp).fillMaxWidth())
        Text(
            stringResource(R.string.calendar_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
