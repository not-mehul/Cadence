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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import app.cadence.core.domain.cycles.DayMark
import app.cadence.core.ui.expressive.CadenceShapeSet
import app.cadence.core.ui.expressive.scaledPath
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
    // The selection grows from a circle into a scalloped cookie, with an expressive spring.
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "selection",
    )
    val textColor =
        when {
            mark == DayMark.LOGGED_PERIOD -> colors.onPeriod
            mark == DayMark.FERTILE || mark == DayMark.OVULATION -> colors.onFertileWindow
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
    val markerColor =
        when (mark) {
            DayMark.LOGGED_PERIOD -> colors.onPeriod
            DayMark.FERTILE, DayMark.OVULATION -> colors.onFertileWindow
            else -> scheme.onSurface
        }
    val selectionMorph = remember { Morph(CadenceShapeSet.circle, CadenceShapeSet.selectedDay) }
    val selectionColor = scheme.primary
    val selectionFill = scheme.primaryContainer

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
                    if (selection > 0f) {
                        drawSelection(
                            selectionMorph,
                            selection,
                            selectionColor,
                            selectionFill.takeIf {
                                mark ==
                                    DayMark.NONE
                            },
                        )
                    }
                }.padding(5.dp)
                .dayMarkBackground(mark, colors)
                .then(if (isToday) Modifier.todayRing(markerColor) else Modifier),
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
                    .padding(bottom = 4.dp)
                    .size(5.dp)
                    .background(markerColor, CircleShape),
            )
        }
    }
}

/** A scalloped ring around the selected day, filled softly when the day has no other mark. */
private fun DrawScope.drawSelection(
    morph: Morph,
    progress: Float,
    color: Color,
    fill: Color?,
) {
    val inset = 1.dp.toPx()
    val side = size.minDimension - inset * 2
    val scale = SELECTION_START_SCALE + (1f - SELECTION_START_SCALE) * progress
    val drawn = side * scale
    val path = morph.scaledPath(progress, Size(drawn, drawn))
    translate((size.width - drawn) / 2, (size.height - drawn) / 2) {
        fill?.let { drawPath(path, it.copy(alpha = progress)) }
        drawPath(path, color.copy(alpha = progress), style = Stroke(width = 2.5.dp.toPx()))
    }
}

private const val SELECTION_START_SCALE = 0.7f

/** A thin solid ring inside the cell; sits inside the dashed predicted ring when both apply. */
private fun Modifier.todayRing(color: Color): Modifier =
    drawBehind {
        val stroke = 1.5.dp.toPx()
        drawCircle(color = color, radius = size.minDimension / 2 - 4.dp.toPx(), style = Stroke(width = stroke))
    }

internal fun Modifier.dayMarkBackground(
    mark: DayMark,
    colors: CycleColors,
): Modifier =
    when (mark) {
        // Peach, with a deeper ring so the day stands out from the card.
        DayMark.LOGGED_PERIOD -> {
            drawBehind {
                val stroke = 1.5.dp.toPx()
                drawCircle(colors.period)
                drawCircle(colors.periodOutline, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            }
        }

        DayMark.FERTILE -> {
            background(colors.fertileWindow, CircleShape)
        }

        // A sun: ovulation differs from the fertile days by shape, not just colour.
        DayMark.OVULATION -> {
            drawBehind {
                val path = CadenceShapeSet.ovulation.scaledPath(size)
                drawPath(path, colors.fertileWindow)
                drawPath(path, colors.ovulation, style = Stroke(width = 1.5.dp.toPx()))
            }
        }

        // Soft pink with a dashed edge: an estimate, unlike the solid logged days.
        DayMark.PREDICTED_PERIOD -> {
            drawBehind {
                val stroke = 2.dp.toPx()
                drawCircle(colors.predictedPeriodContainer)
                drawCircle(
                    color = colors.predictedPeriod,
                    radius = size.minDimension / 2 - stroke / 2,
                    center = Offset(size.width / 2, size.height / 2),
                    style =
                        Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx())),
                        ),
                )
            }
        }

        DayMark.NONE -> {
            this
        }
    }

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
            Box(Modifier.size(18.dp).dayMarkBackground(DayMark.LOGGED_PERIOD, colors))
        }
        LegendItem(stringResource(R.string.calendar_legend_predicted)) {
            Box(Modifier.size(18.dp).dayMarkBackground(DayMark.PREDICTED_PERIOD, colors))
        }
        if (showFertileWindow) {
            LegendItem(stringResource(R.string.calendar_legend_fertile)) {
                Box(Modifier.size(18.dp).dayMarkBackground(DayMark.FERTILE, colors))
            }
            LegendItem(stringResource(R.string.calendar_legend_ovulation)) {
                Box(Modifier.size(18.dp).dayMarkBackground(DayMark.OVULATION, colors))
            }
        }
        LegendItem(stringResource(R.string.calendar_legend_today)) {
            Box(Modifier.size(18.dp).todayRing(MaterialTheme.colorScheme.onSurface))
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
