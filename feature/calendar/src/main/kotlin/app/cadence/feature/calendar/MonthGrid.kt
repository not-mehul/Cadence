package app.cadence.feature.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cadence.core.domain.cycles.DayMark
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.currentLocale
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.theme.CadenceTheme
import app.cadence.core.ui.theme.CycleColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
internal fun MonthGrid(
    month: YearMonth,
    marks: Map<LocalDate, DayMark>,
    state: CalendarUiState.Ready,
    editPeriods: Boolean,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekdays = remember(firstDayOfWeek) { (0L until 7L).map { firstDayOfWeek.plus(it) } }
    val leadingBlanks = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map(month::atDay)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            weekdays.forEach { day -> WeekdayLabel(day, locale, Modifier.weight(1f)) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                mark = marks[date] ?: DayMark.NONE,
                                isToday = date == state.today,
                                selected = !editPeriods && date == state.selected,
                                logged = state.isLogged(date),
                                hasDetails = date in state.detailDates,
                                editPeriods = editPeriods,
                                enabled = !editPeriods || state.canToggle(date),
                                onToggle = { actions.onToggle(date) },
                                onSelect = { actions.onSelect(date) },
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WeekdayLabel(
    day: DayOfWeek,
    locale: java.util.Locale,
    modifier: Modifier = Modifier,
) {
    Text(
        text = day.getDisplayName(TextStyle.NARROW, locale),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.padding(vertical = CadenceTheme.spacing.sm),
    )
}

@Composable
private fun DayCell(
    date: LocalDate,
    mark: DayMark,
    isToday: Boolean,
    selected: Boolean,
    logged: Boolean,
    hasDetails: Boolean,
    editPeriods: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = CadenceTheme.cycleColors
    val scheme = MaterialTheme.colorScheme
    // The selection ring fades in and the day gives a small, springy pop.
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "selection",
    )
    val textColor =
        when {
            mark == DayMark.LOGGED_PERIOD -> colors.onPeriod
            mark == DayMark.OVULATION -> colors.onOvulation
            mark == DayMark.FERTILE -> colors.onFertileWindow
            mark == DayMark.NONE && selected -> scheme.onPrimaryContainer
            !enabled -> scheme.onSurfaceVariant
            else -> scheme.onSurface
        }
    val description =
        buildList {
            add(rememberDateFormatter(DateSkeletons.DAY_MONTH_YEAR).format(date))
            if (mark == DayMark.PREDICTED_PERIOD) add(stringResource(R.string.calendar_desc_predicted))
            if (mark == DayMark.FERTILE) add(stringResource(R.string.calendar_desc_fertile))
            if (mark == DayMark.OVULATION) add(stringResource(R.string.calendar_desc_ovulation))
            if (isToday) add(stringResource(R.string.calendar_desc_today))
            if (hasDetails) add(stringResource(R.string.calendar_desc_has_log))
        }.joinToString(", ")
    val state = stringResource(if (logged) R.string.calendar_state_period else R.string.calendar_state_not_period)
    val ringColor = scheme.primary
    val todayColor = scheme.onSurfaceVariant
    val softFill = scheme.primaryContainer

    val interaction =
        if (editPeriods) {
            Modifier.toggleable(value = logged, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
        } else {
            Modifier.selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onSelect)
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // The whole cell is tappable; the gap between days is drawn inside it.
                .then(interaction)
                .semantics {
                    contentDescription = description
                    if (editPeriods) stateDescription = state
                }.drawBehind {
                    // One outer ring at most: the selection, or else today. They cross-fade.
                    val radius = size.minDimension / 2 - OUTER_RING_INSET.toPx()
                    if (isToday && selection < 1f) {
                        drawCircle(todayColor.copy(alpha = 1f - selection), radius, style = Stroke(TODAY_STROKE.toPx()))
                    }
                    if (selection > 0f) {
                        drawCircle(ringColor.copy(alpha = selection), radius, style = Stroke(SELECTED_STROKE.toPx()))
                    }
                }.padding(FILL_INSET)
                .graphicsLayer {
                    val pop = 1f + POP * selection
                    scaleX = pop
                    scaleY = pop
                }.drawBehind {
                    if (mark == DayMark.NONE && selection > 0f) drawCircle(softFill.copy(alpha = selection))
                }
                // The dash is the predicted day's only line, so it gives way to the today and selection rings.
                .dayMarkFill(mark, colors, dashed = !isToday && !selected),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday || selected) FontWeight.Bold else null,
            color = textColor,
        )
        if (hasDetails) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(5.dp)
                    .background(textColor, CircleShape),
            )
        }
    }
}

private val OUTER_RING_INSET = 2.dp
private val FILL_INSET = 6.dp
private val TODAY_STROKE = 1.5.dp
private val SELECTED_STROKE = 2.5.dp
private const val POP = 0.06f

/**
 * The day's mark as a plain circle: peach for a logged period, teal for the fertile window and
 * a deeper teal for ovulation. A predicted period is soft pink with a dashed edge ([dashed]),
 * so logged and predicted differ in line as well as colour.
 */
internal fun Modifier.dayMarkFill(
    mark: DayMark,
    colors: CycleColors,
    dashed: Boolean = true,
): Modifier =
    when (mark) {
        DayMark.LOGGED_PERIOD -> {
            background(colors.period, CircleShape)
        }

        DayMark.FERTILE -> {
            background(colors.fertileWindow, CircleShape)
        }

        DayMark.OVULATION -> {
            background(colors.ovulation, CircleShape)
        }

        DayMark.PREDICTED_PERIOD -> {
            drawBehind {
                drawCircle(colors.predictedPeriodContainer)
                if (dashed) {
                    val stroke = 1.5.dp.toPx()
                    drawCircle(
                        color = colors.predictedPeriod,
                        radius = size.minDimension / 2 - stroke / 2,
                        style =
                            Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                            ),
                    )
                }
            }
        }

        DayMark.NONE -> {
            this
        }
    }

/** Today's ring, for the legend. */
private fun Modifier.todayRing(color: Color): Modifier =
    drawBehind { drawCircle(color, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(TODAY_STROKE.toPx())) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Legend(
    showFertileWindow: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = CadenceTheme.cycleColors
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.lg, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(stringResource(R.string.calendar_legend_period)) {
            Box(Modifier.size(18.dp).dayMarkFill(DayMark.LOGGED_PERIOD, colors))
        }
        LegendItem(stringResource(R.string.calendar_legend_predicted)) {
            Box(Modifier.size(18.dp).dayMarkFill(DayMark.PREDICTED_PERIOD, colors))
        }
        if (showFertileWindow) {
            LegendItem(stringResource(R.string.calendar_legend_fertile)) {
                Box(Modifier.size(18.dp).dayMarkFill(DayMark.FERTILE, colors))
            }
            LegendItem(stringResource(R.string.calendar_legend_ovulation)) {
                Box(Modifier.size(18.dp).dayMarkFill(DayMark.OVULATION, colors))
            }
        }
        LegendItem(stringResource(R.string.calendar_legend_today)) {
            Box(Modifier.size(18.dp).todayRing(MaterialTheme.colorScheme.onSurfaceVariant))
        }
        LegendItem(stringResource(R.string.calendar_legend_logged)) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape))
            }
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    swatch: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.xs),
    ) {
        swatch()
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
