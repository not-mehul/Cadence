package app.cadence.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cadence.core.domain.patterns.CycleDayHeatmap
import app.cadence.core.domain.patterns.HeatmapRow
import app.cadence.core.ui.components.CadenceCard
import app.cadence.core.ui.components.CardHeader
import app.cadence.core.ui.log.LogIcons
import app.cadence.core.ui.log.LogLabels
import app.cadence.core.ui.theme.CadenceTheme
import app.cadence.core.ui.R as UiR

/**
 * "By cycle day": one row per thing logged, one cell per cycle day, deeper colour where it was
 * logged in more of the recent cycles. The period days are banded underneath. Each row reads out
 * as words for TalkBack ("Cramps: most often on cycle days 1 to 3").
 */
@Composable
internal fun CycleDayCard(
    heatmap: CycleDayHeatmap,
    tagLabels: Map<Long, String>,
    modifier: Modifier = Modifier,
) {
    val spacing = CadenceTheme.spacing
    CadenceCard(modifier) {
        CardHeader(
            stringResource(R.string.insights_heatmap_title),
            icon = painterResource(UiR.drawable.ms_calendar_month),
            accent = MaterialTheme.colorScheme.secondary,
            accentContainer = MaterialTheme.colorScheme.secondaryContainer,
        )
        Text(
            pluralStringResource(R.plurals.insights_heatmap_body, heatmap.cycles, heatmap.cycles),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = spacing.md),
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            heatmap.rows.forEach { row -> HeatmapRowView(row, heatmap, tagLabels) }
            DayAxis(heatmap)
        }
        Text(
            stringResource(R.string.insights_heatmap_key),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.md),
        )
    }
}

private val LABEL_WIDTH = 104.dp
private val ROW_HEIGHT = 22.dp

@Composable
private fun HeatmapRowView(
    row: HeatmapRow,
    heatmap: CycleDayHeatmap,
    tagLabels: Map<Long, String>,
) {
    val name = LogLabels.item(row.itemId, tagLabels)
    val description = stringResource(R.string.insights_heatmap_row, name, dayRanges(row.busiestDays()))
    val fill = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_ALPHA)
    val band = CadenceTheme.cycleColors.period.copy(alpha = BAND_ALPHA)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.width(LABEL_WIDTH),
        ) {
            Icon(painterResource(LogIcons.item(row.itemId)), null, Modifier.size(18.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Canvas(Modifier.weight(1f).height(ROW_HEIGHT)) {
            val cell = size.width / heatmap.days
            val gap = 1.5.dp.toPx()
            val radius = CornerRadius(3.dp.toPx())
            // The period days, behind the cells.
            drawRoundRect(band, size = Size(cell * heatmap.periodDays, size.height), cornerRadius = radius)
            row.cyclesByDay.forEachIndexed { day, count ->
                val share = count.toFloat() / heatmap.cycles
                drawRoundRect(
                    color = if (count == 0) empty else fill.copy(alpha = MIN_ALPHA + (1f - MIN_ALPHA) * share),
                    topLeft = Offset(day * cell + gap / 2, gap),
                    size = Size(cell - gap, size.height - gap * 2),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** Day numbers under the grid at 1, 7, 14, 21, 28... placed by proportion. Decorative. */
@Composable
private fun DayAxis(heatmap: CycleDayHeatmap) {
    val marks = listOf(1) + (7..heatmap.days step 7)
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Box(Modifier.width(LABEL_WIDTH))
        Layout(
            content = {
                marks.forEach { day ->
                    Text(
                        day.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.weight(1f),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val cell = constraints.maxWidth.toFloat() / heatmap.days
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(constraints.maxWidth, height) {
                placeables.zip(marks).forEach { (p, day) ->
                    val centre = (day - 0.5f) * cell
                    p.placeRelative((centre - p.width / 2f).toInt().coerceIn(0, constraints.maxWidth - p.width), 0)
                }
            }
        }
    }
}

/** 1, 2, 3, 21 -> "1-3, 21" (with an en dash). */
internal fun dayRanges(days: List<Int>): String =
    days
        .sorted()
        .fold(mutableListOf<IntRange>()) { ranges, day ->
            val last = ranges.lastOrNull()
            if (last != null && last.last + 1 == day) ranges[ranges.lastIndex] = last.first..day else ranges += day..day
            ranges
        }.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}\u2013${it.last}" }

private const val EMPTY_ALPHA = 0.06f
private const val MIN_ALPHA = 0.25f
private const val BAND_ALPHA = 0.35f
