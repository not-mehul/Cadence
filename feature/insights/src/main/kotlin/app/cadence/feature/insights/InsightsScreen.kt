package app.cadence.feature.insights

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.cycles.Confidence
import app.cadence.core.domain.cycles.HealthFlag
import app.cadence.core.domain.cycles.PredictionBasis
import app.cadence.core.domain.patterns.PatternTiming
import app.cadence.core.domain.patterns.SymptomPattern
import app.cadence.core.ui.components.CadenceCard
import app.cadence.core.ui.components.CardHeader
import app.cadence.core.ui.components.IconDisc
import app.cadence.core.ui.components.ScreenTitle
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.log.LogLabels
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate
import kotlin.math.roundToInt
import app.cadence.core.ui.R as UiR

@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        InsightsUiState.Loading -> Box(modifier.fillMaxSize())
        is InsightsUiState.Ready -> InsightsContent(s, onIncludeChange = viewModel::setIncluded, modifier = modifier)
    }
}

/** Stateless Insights content; previewable and testable without a view model. */
@Composable
fun InsightsContent(
    state: InsightsUiState.Ready,
    onIncludeChange: (LocalDate, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CadenceTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenGutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        ScreenTitle(stringResource(R.string.insights_title))
        if (state.cycles.isEmpty()) {
            EmptyState(hasPeriod = state.currentCycle != null)
        } else {
            SummaryTiles(state)
            ChartCard(state.chartCycles)
        }
        if (state.flags.isNotEmpty()) FlagsCard(state.flags)
        if (state.currentCycle != null) PatternsCard(state.patterns, state.tagLabels)
        state.heatmap?.takeIf { it.rows.isNotEmpty() }?.let { CycleDayCard(it, state.tagLabels) }
        if (state.currentCycle != null) HistoryCard(state, onIncludeChange)
    }
}

@Composable
private fun EmptyState(hasPeriod: Boolean) {
    CadenceCard {
        CardTitle(
            stringResource(if (hasPeriod) R.string.insights_first_cycle_title else R.string.insights_empty_title),
            UiR.drawable.ms_auto_awesome,
        )
        Text(
            stringResource(if (hasPeriod) R.string.insights_first_cycle_body else R.string.insights_empty_body),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CardTitle(
    text: String,
    @DrawableRes icon: Int,
) {
    CardHeader(
        text,
        icon = painterResource(icon),
        accent = MaterialTheme.colorScheme.secondary,
        accentContainer = MaterialTheme.colorScheme.secondaryContainer,
    )
}

@Composable
private fun days(value: Int): String = pluralStringResource(UiR.plurals.days_count, value, value)

@Composable
private fun SummaryTiles(state: InsightsUiState.Ready) {
    val stats = state.stats
    val none = stringResource(R.string.insights_no_value)
    val prediction = state.prediction
    Column(verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md)) {
        Row(
            Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
        ) {
            StatTile(
                icon = UiR.drawable.ms_event_repeat,
                label = stringResource(R.string.insights_average_cycle),
                value = stats.averageCycleLength?.let { days(it.roundToInt()) } ?: none,
                // "28 days to 28 days" says nothing: show the range only when cycles differ.
                detail =
                    rangeOf(stats.shortestCycle, stats.longestCycle)?.let { (shortest, longest) ->
                        stringResource(R.string.insights_range, days(shortest), days(longest))
                    },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                icon = UiR.drawable.ms_water_drop,
                label = stringResource(R.string.insights_average_period),
                value = stats.averagePeriodLength?.let { days(it.roundToInt()) } ?: none,
                detail = null,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
        ) {
            StatTile(
                icon = UiR.drawable.ms_straighten,
                label = stringResource(R.string.insights_variation),
                value = stats.variation?.let { days(it) } ?: none,
                detail = stats.variation?.let { stringResource(R.string.insights_shortest_to_longest) },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                icon = UiR.drawable.ms_target,
                label = stringResource(R.string.insights_prediction),
                value = prediction?.let { stringResource(confidenceLabel(it.confidence)) } ?: none,
                detail =
                    prediction?.let {
                        if (it.basis == PredictionBasis.TYPICAL) {
                            stringResource(R.string.insights_based_on_typical)
                        } else {
                            stringResource(R.string.insights_plus_minus, days(it.rangeDays))
                        }
                    },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@StringRes
private fun confidenceLabel(confidence: Confidence): Int =
    when (confidence) {
        Confidence.HIGH -> R.string.insights_confidence_high
        Confidence.MEDIUM -> R.string.insights_confidence_medium
        Confidence.LOW -> R.string.insights_confidence_low
    }

@Composable
private fun StatTile(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    CadenceCard(modifier.fillMaxHeight()) {
        IconDisc(
            painterResource(icon),
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer,
            size = 36.dp,
            modifier = Modifier.padding(bottom = CadenceTheme.spacing.sm),
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(cycles: List<CycleRow>) {
    var selected by rememberSaveable(cycles.size) { mutableIntStateOf(cycles.lastIndex) }
    val dateFormat = rememberDateFormatter(DateSkeletons.DAY_MONTH_SHORT)
    val chosen = cycles.getOrNull(selected) ?: cycles.last()
    val description =
        stringResource(R.string.insights_chart_description, cycles.joinToString(", ") { it.lengthDays.toString() })
    CadenceCard {
        CardTitle(stringResource(R.string.insights_chart_title), UiR.drawable.ms_bar_chart)
        CycleLengthChart(
            cycles = cycles,
            selectedIndex = selected,
            onSelect = { selected = it },
            description = description,
        )
        Text(
            stringResource(
                if (chosen.excluded) R.string.insights_chart_selected_excluded else R.string.insights_chart_selected,
                dateFormat.format(chosen.start),
                days(chosen.lengthDays),
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = CadenceTheme.spacing.sm),
        )
        Text(
            stringResource(R.string.insights_chart_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlagsCard(flags: List<HealthFlag>) {
    CadenceCard {
        CardTitle(stringResource(R.string.insights_flags_title), UiR.drawable.ms_info)
        Column(verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md)) {
            flags.forEach { flag ->
                val (title, body) = flagCopy(flag)
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                stringResource(R.string.insights_flags_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun flagCopy(flag: HealthFlag): Pair<String, String> =
    when (flag) {
        is HealthFlag.ShortCycles -> {
            stringResource(R.string.flag_short_title) to
                stringResource(R.string.flag_short_body, days(flag.averageDays))
        }

        is HealthFlag.LongCycles -> {
            stringResource(R.string.flag_long_title) to stringResource(R.string.flag_long_body, days(flag.averageDays))
        }

        is HealthFlag.IrregularCycles -> {
            stringResource(R.string.flag_irregular_title) to
                stringResource(R.string.flag_irregular_body, days(flag.variationDays))
        }

        is HealthFlag.LongPeriod -> {
            val start = rememberDateFormatter(DateSkeletons.DAY_MONTH_YEAR).format(flag.start)
            stringResource(R.string.flag_long_period_title) to
                stringResource(R.string.flag_long_period_body, start, days(flag.lengthDays))
        }

        is HealthFlag.NoRecentPeriod -> {
            stringResource(R.string.flag_no_period_title) to
                stringResource(R.string.flag_no_period_body, days(flag.daysSinceLastPeriod))
        }

        is HealthFlag.SeverePain -> {
            stringResource(R.string.flag_pain_title) to stringResource(R.string.flag_pain_body, flag.cycles.toString())
        }
    }

@Composable
private fun HistoryCard(
    state: InsightsUiState.Ready,
    onIncludeChange: (LocalDate, Boolean) -> Unit,
) {
    val dateFormat = rememberDateFormatter(DateSkeletons.DAY_MONTH_SHORT)
    CadenceCard {
        CardTitle(stringResource(R.string.insights_history_title), UiR.drawable.ms_history)
        state.currentCycle?.let { current ->
            Text(stringResource(R.string.insights_current_cycle), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.insights_current_cycle_detail, dateFormat.format(current.start), current.day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.cycles.isNotEmpty()) {
            Text(
                stringResource(R.string.insights_history_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CadenceTheme.spacing.md),
            )
        }
        state.cycles.asReversed().forEach { cycle ->
            HorizontalDivider(Modifier.padding(vertical = CadenceTheme.spacing.sm))
            CycleHistoryRow(cycle, dateFormat.format(cycle.start), dateFormat.format(cycle.end), onIncludeChange)
        }
    }
}

@Composable
private fun CycleHistoryRow(
    cycle: CycleRow,
    start: String,
    end: String,
    onIncludeChange: (LocalDate, Boolean) -> Unit,
) {
    val included = !cycle.excluded
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = included, role = Role.Switch, onValueChange = { onIncludeChange(cycle.start, it) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.insights_cycle_dates, start, end), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (included) {
                    stringResource(
                        R.string.insights_cycle_detail,
                        pluralStringResource(R.plurals.insights_day_length, cycle.lengthDays, cycle.lengthDays),
                        pluralStringResource(R.plurals.insights_day_length, cycle.periodDays, cycle.periodDays),
                    )
                } else {
                    stringResource(R.string.insights_cycle_excluded)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = included, onCheckedChange = null)
    }
}

/** Symptom patterns by cycle timing. */
@Composable
private fun PatternsCard(
    patterns: List<SymptomPattern>,
    tagLabels: Map<Long, String>,
) {
    CadenceCard {
        CardTitle(stringResource(R.string.insights_patterns_title), UiR.drawable.ms_auto_awesome)
        if (patterns.isEmpty()) {
            Text(stringResource(R.string.insights_patterns_empty), style = MaterialTheme.typography.bodyMedium)
            return@CadenceCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm)) {
            patterns.forEach { pattern ->
                Column {
                    Text(LogLabels.item(pattern.itemId, tagLabels), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            R.string.insights_pattern_line,
                            patternTiming(pattern),
                            pluralStringResource(
                                R.plurals.insights_pattern_cycles,
                                pattern.outOf,
                                pattern.cycles,
                                pattern.outOf,
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(R.string.insights_patterns_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun patternTiming(pattern: SymptomPattern): String {
    val day = pattern.day ?: 0
    return when (pattern.timing) {
        PatternTiming.DURING_PERIOD -> stringResource(R.string.insights_pattern_during)
        PatternTiming.BEFORE_PERIOD -> pluralStringResource(R.plurals.insights_pattern_before, day, day)
        PatternTiming.AROUND_CYCLE_DAY -> stringResource(R.string.insights_pattern_cycle_day, day)
    }
}

/** Shortest and longest, when both are known and differ. */
internal fun rangeOf(
    shortest: Int?,
    longest: Int?,
): Pair<Int, Int>? = if (shortest != null && longest != null && shortest != longest) shortest to longest else null
