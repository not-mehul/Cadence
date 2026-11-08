package app.cadence.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cadence.core.ui.theme.CadenceTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A coloured stretch of the ring, in cycle days (1-based, inclusive). */
data class RingSegment(
    val firstDay: Int,
    val lastDay: Int,
    val color: Color,
)

/**
 * The cycle as a ring: one arc per phase, with a marker on today. Phases are also named
 * in text nearby, so colour is never the only cue. The ring is a single TalkBack node
 * described by [contentDescription]; pass null when it is purely decorative.
 */
@Composable
fun CycleRing(
    cycleLength: Int,
    today: Int,
    segments: List<RingSegment>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val markerFill = MaterialTheme.colorScheme.surface
    val markerStroke = MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clearAndSetSemantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Proportions tuned at 280dp (18dp stroke, 13dp marker) and scaled for other sizes.
            val side = min(size.width, size.height)
            val strokeWidth = side * STROKE_FRACTION
            val markerRadius = side * MARKER_FRACTION
            val diameter = side - 2 * markerRadius - 2.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val degreesPerDay = 360f / cycleLength
            val gap = if (segments.size > 1) 2f else 0f

            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
            segments.forEach { s ->
                val first = s.firstDay.coerceIn(1, cycleLength)
                val last = s.lastDay.coerceIn(first, cycleLength)
                val start = -90f + (first - 1) * degreesPerDay + gap / 2
                val sweep = ((last - first + 1) * degreesPerDay - gap).coerceAtLeast(0.5f)
                drawArc(
                    s.color,
                    start,
                    sweep,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                )
            }

            // Marker in the middle of today's slot; a late period pins it at the end of the ring.
            val dayPosition = (today.coerceIn(1, cycleLength) - 0.5f) * degreesPerDay
            val angle = Math.toRadians((-90f + dayPosition).toDouble())
            val radius = diameter / 2
            val centre =
                Offset(
                    size.width / 2 + radius * cos(angle).toFloat(),
                    size.height / 2 + radius * sin(angle).toFloat(),
                )
            drawCircle(markerFill, markerRadius, centre)
            drawCircle(markerStroke, markerRadius, centre, style = Stroke(markerRadius * MARKER_OUTLINE_FRACTION))
        }
        center()
    }
}

private const val STROKE_FRACTION = 18f / 280f
private const val MARKER_FRACTION = 13f / 280f
private const val MARKER_OUTLINE_FRACTION = 3f / 13f

@Preview(showBackground = true, widthDp = 280)
@Composable
private fun CycleRingPreview() {
    CadenceTheme {
        val c = CadenceTheme.cycleColors
        CycleRing(
            cycleLength = 28,
            today = 12,
            segments =
                listOf(
                    RingSegment(1, 5, c.menstrualPhase),
                    RingSegment(6, 9, c.follicularPhase),
                    RingSegment(10, 16, c.ovulatoryPhase),
                    RingSegment(17, 28, c.lutealPhase),
                ),
            contentDescription = "Cycle day 12 of about 28",
        ) {
            Text("Day 12", style = MaterialTheme.typography.displaySmall)
        }
    }
}
