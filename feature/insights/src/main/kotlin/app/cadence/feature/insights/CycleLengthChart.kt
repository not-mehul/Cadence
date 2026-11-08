package app.cadence.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.cadence.core.domain.cycles.CycleInsights
import app.cadence.core.ui.format.rememberDateFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * One bar per cycle, oldest on the left, on a zero-based axis with the typical 24–38 day
 * range shaded. Single series, so no legend; the selected bar is described in text below
 * the chart, and the cycle history list is the table view of the same data.
 */
@Composable
internal fun CycleLengthChart(
    cycles: List<CycleRow>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val monthFormat = rememberDateFormatter("MMM")
    val colors = MaterialTheme.colorScheme
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface)
    val longest = cycles.maxOfOrNull { it.lengthDays } ?: 0
    val yMax = max(AXIS_MIN_TOP, (ceil((longest + 3) / GRID_STEP.toDouble()) * GRID_STEP).toInt())

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(200.dp)
                .clearAndSetSemantics { contentDescription = description }
                .pointerInput(cycles.size) {
                    detectTapGestures { tap ->
                        val left = Y_AXIS_WIDTH.dp.toPx()
                        val slot = (size.width - left) / cycles.size
                        val index = ((tap.x - left) / slot).toInt()
                        if (index in cycles.indices) onSelect(index)
                    }
                },
    ) {
        val left = Y_AXIS_WIDTH.dp.toPx()
        val top = 20.dp.toPx()
        val bottom = size.height - X_AXIS_HEIGHT.dp.toPx()
        val plotHeight = bottom - top

        fun y(days: Int) = bottom - plotHeight * days / yMax

        // Typical range band, then recessive gridlines and their labels.
        val band = CycleInsights.NORMAL_CYCLE_RANGE
        drawRect(
            colors.surfaceContainerHighest,
            Offset(left, y(band.last)),
            Size(
                size.width - left,
                y(band.first) - y(band.last),
            ),
        )
        for (value in 0..yMax step GRID_STEP) {
            drawLine(
                colors.outlineVariant,
                Offset(left, y(value)),
                Offset(size.width, y(value)),
                strokeWidth = 1.dp.toPx(),
            )
            val label = measurer.measure(value.toString(), axisStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(value) - label.size.height / 2))
        }
        if (cycles.isEmpty()) return@Canvas

        val slot = (size.width - left) / cycles.size
        val barWidth = min(MAX_BAR_WIDTH.dp.toPx(), slot - BAR_GAP.dp.toPx())
        val radius = 4.dp.toPx()
        cycles.forEachIndexed { i, cycle ->
            val x = left + slot * i + (slot - barWidth) / 2
            val barTop = y(cycle.lengthDays)
            val path =
                Path().apply {
                    // Rounded data end, square at the baseline.
                    addRoundRect(
                        RoundRect(
                            left = x,
                            top = barTop,
                            right = x + barWidth,
                            bottom = bottom,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                        ),
                    )
                }
            val color =
                when {
                    cycle.excluded -> colors.outlineVariant
                    i == selectedIndex -> colors.primary
                    else -> colors.primary.copy(alpha = 0.55f)
                }
            drawPath(path, color)
            if (i == selectedIndex) {
                val value = measurer.measure(cycle.lengthDays.toString(), valueStyle)
                drawText(
                    value,
                    topLeft =
                        Offset(
                            x + barWidth / 2 - value.size.width / 2,
                            barTop - value.size.height - 2.dp.toPx(),
                        ),
                )
            }
        }

        // Month labels for the first and last bars only, so they never collide.
        listOf(0, cycles.lastIndex).distinct().forEach { i ->
            val label = measurer.measure(monthFormat.format(cycles[i].start), axisStyle)
            val centre = left + slot * i + slot / 2
            val x = (centre - label.size.width / 2).coerceIn(left, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, bottom + 4.dp.toPx()))
        }
    }
}

private const val Y_AXIS_WIDTH = 28
private const val X_AXIS_HEIGHT = 20
private const val GRID_STEP = 15
private const val AXIS_MIN_TOP = 45
private const val MAX_BAR_WIDTH = 24
private const val BAR_GAP = 2
