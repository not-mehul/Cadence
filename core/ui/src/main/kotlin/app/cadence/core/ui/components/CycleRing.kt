package app.cadence.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import app.cadence.core.ui.expressive.rememberAnimationsEnabled
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
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val markerFill = MaterialTheme.colorScheme.surfaceContainerLowest
    val markerStroke = MaterialTheme.colorScheme.onSurface
    // The marker travels from the start of the cycle to today when the ring first appears.
    // (With animations turned off in system settings it simply appears in place.)
    val position = remember { Animatable(0f) }
    // A soft "you are here" ripple spreads from the marker every couple of seconds.
    val pulse =
        if (rememberAnimationsEnabled()) {
            rememberInfiniteTransition(label = "marker").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing)),
                label = "pulse",
            )
        } else {
            null
        }
    LaunchedEffect(today, cycleLength) {
        position.animateTo(
            today.coerceIn(1, cycleLength) - 0.5f,
            tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
    }
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
            val dayPosition = position.value * degreesPerDay
            val angle = Math.toRadians((-90f + dayPosition).toDouble())
            val radius = diameter / 2
            val centre =
                Offset(
                    size.width / 2 + radius * cos(angle).toFloat(),
                    size.height / 2 + radius * sin(angle).toFloat(),
                )
            // A soft halo lifts the marker off the ring.
            pulse?.value?.let { p ->
                drawCircle(
                    markerStroke.copy(alpha = PULSE_ALPHA * (1f - p)),
                    markerRadius * (1.35f + PULSE_GROWTH * p),
                    centre,
                )
            }
            drawCircle(markerStroke.copy(alpha = 0.12f), markerRadius * 1.35f, centre)
            drawCircle(markerFill, markerRadius, centre)
            drawCircle(markerStroke, markerRadius, centre, style = Stroke(markerRadius * MARKER_OUTLINE_FRACTION))
        }
        center()
    }
}

private const val PULSE_MS = 2_400
private const val PULSE_ALPHA = 0.22f
private const val PULSE_GROWTH = 1.1f

private const val STROKE_FRACTION = 16f / 280f
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
                    RingSegment(1, 5, c.menstrualRing),
                    RingSegment(6, 9, c.follicularRing),
                    RingSegment(10, 16, c.ovulatoryRing),
                    RingSegment(17, 28, c.lutealRing),
                ),
            contentDescription = "Cycle day 12 of about 28",
        ) {
            Text("Day 12", style = MaterialTheme.typography.displaySmall)
        }
    }
}
