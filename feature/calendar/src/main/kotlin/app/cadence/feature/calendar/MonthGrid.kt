package app.cadence.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
internal fun MonthGrid(
    month: YearMonth,
    marks: Map<LocalDate, DayMark>,
    today: LocalDate,
    isLogged: (LocalDate) -> Boolean,
    canToggle: (LocalDate) -> Boolean,
    onToggle: (LocalDate) -> Unit,
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
                                isToday = date == today,
                                logged = isLogged(date),
                                enabled = canToggle(date),
                                onToggle = { onToggle(date) },
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
    logged: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val colors = CadenceTheme.cycleColors
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textColor =
        when {
            mark == DayMark.LOGGED_PERIOD -> colors.onPeriod
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> onSurface
        }
    val description =
        buildList {
            add(rememberDateFormatter(DateSkeletons.DAY_MONTH_YEAR).format(date))
            if (mark == DayMark.PREDICTED_PERIOD) add(stringResource(R.string.calendar_desc_predicted))
            if (isToday) add(stringResource(R.string.calendar_desc_today))
        }.joinToString(", ")
    val state = stringResource(if (logged) R.string.calendar_state_period else R.string.calendar_state_not_period)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(3.dp)
                .toggleable(value = logged, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics {
                    contentDescription = description
                    stateDescription = state
                }.dayMarkBackground(mark, colors.period, colors.predictedPeriod),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = textColor,
        )
        if (isToday) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(5.dp)
                    .background(if (mark == DayMark.LOGGED_PERIOD) colors.onPeriod else onSurface, CircleShape),
            )
        }
    }
}

private fun Modifier.dayMarkBackground(
    mark: DayMark,
    period: Color,
    predicted: Color,
): Modifier =
    when (mark) {
        DayMark.LOGGED_PERIOD -> {
            background(period, CircleShape)
        }

        DayMark.PREDICTED_PERIOD -> {
            drawBehind {
                val stroke = 2.dp.toPx()
                drawCircle(
                    color = predicted,
                    radius = size.minDimension / 2 - stroke / 2,
                    style =
                        Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        ),
                )
            }
        }

        DayMark.NONE -> {
            this
        }
    }

@Composable
internal fun Legend(modifier: Modifier = Modifier) {
    val colors = CadenceTheme.cycleColors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.lg, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(stringResource(R.string.calendar_legend_period)) {
            Box(Modifier.size(16.dp).background(colors.period, CircleShape))
        }
        LegendItem(stringResource(R.string.calendar_legend_predicted)) {
            Box(Modifier.size(16.dp).dayMarkBackground(DayMark.PREDICTED_PERIOD, colors.period, colors.predictedPeriod))
        }
        LegendItem(stringResource(R.string.calendar_legend_today)) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
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
