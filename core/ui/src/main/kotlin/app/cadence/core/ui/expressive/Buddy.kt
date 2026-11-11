package app.cadence.core.ui.expressive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How the buddy looks. */
enum class BuddyFace {
    /** Open eyes and a smile. */
    HAPPY,

    /** Eyes closed in happy arcs: for celebrations and greetings. */
    JOYFUL,

    /** Eyes closed and a small mouth: the lock screen, night. */
    SLEEPY,
}

/**
 * Cadence's little companion: a soft Material shape with a face. It bounces in, grows from a
 * circle into its [shape], and blinks a couple of times, then stays still. Purely decorative:
 * it has no semantics, so screen readers skip it.
 */
@Composable
fun Buddy(
    body: Color,
    face: Color,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    expression: BuddyFace = BuddyFace.HAPPY,
    size: Dp = 72.dp,
    cheeks: Color = Color(0xFFFF8FA8),
    /** Waits this long before bouncing in, so a group can arrive one after another. */
    enterDelayMillis: Long = 0,
) {
    val animate = rememberAnimationsEnabled()
    val grow = remember { Animatable(if (animate) 0f else 1f) }
    val bounce = remember { Animatable(if (animate) 0.6f else 1f) }
    val eyes = remember { Animatable(1f) }
    val morph = remember(shape) { Morph(MaterialShapes.Circle, shape) }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        delay(enterDelayMillis)
        launch { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
        launch { bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow)) }
        if (expression == BuddyFace.HAPPY) {
            repeat(BLINKS) {
                delay(BLINK_GAP_MS)
                eyes.animateTo(0.1f, tween(BLINK_MS))
                eyes.animateTo(1f, tween(BLINK_MS))
            }
        }
    }

    Canvas(modifier.size(size)) {
        scale(bounce.value) {
            drawPath(morph.scaledPath(grow.value, this.size), body)
            drawFace(face, cheeks, expression, eyes.value)
        }
    }
}

private fun DrawScope.drawFace(
    face: Color,
    cheeks: Color,
    expression: BuddyFace,
    eyeOpen: Float,
) {
    val w = size.width
    val h = size.height
    val eyeY = h * 0.46f
    val eyeDx = w * 0.13f
    val stroke = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
    listOf(-1f, 1f).forEach { side ->
        val cx = w / 2 + side * eyeDx
        when (expression) {
            BuddyFace.HAPPY -> {
                val rx = w * 0.042f
                val ry = w * 0.058f * eyeOpen
                drawOval(face, Offset(cx - rx, eyeY - ry), Size(rx * 2, ry * 2))
            }

            // ^ ^ : arcs bulging upwards.
            BuddyFace.JOYFUL -> {
                drawArc(
                    face,
                    180f,
                    180f,
                    false,
                    Offset(cx - w * 0.06f, eyeY - w * 0.03f),
                    Size(w * 0.12f, w * 0.1f),
                    style = stroke,
                )
            }

            // Closed eyes: arcs bulging downwards.
            BuddyFace.SLEEPY -> {
                drawArc(
                    face,
                    0f,
                    180f,
                    false,
                    Offset(cx - w * 0.06f, eyeY - w * 0.06f),
                    Size(w * 0.12f, w * 0.08f),
                    style = stroke,
                )
            }
        }
        drawOval(
            cheeks.copy(alpha = 0.55f),
            Offset(w / 2 + side * w * 0.24f - w * 0.06f, h * 0.55f),
            Size(w * 0.12f, w * 0.07f),
        )
    }
    val mouthWidth = if (expression == BuddyFace.SLEEPY) w * 0.08f else w * 0.14f
    drawArc(
        face,
        0f,
        180f,
        false,
        Offset(w / 2 - mouthWidth / 2, h * 0.53f),
        Size(mouthWidth, w * 0.09f),
        style = stroke,
    )
}

private const val BLINKS = 2
private const val BLINK_GAP_MS = 1_100L
private const val BLINK_MS = 90

/**
 * The four phases as friends: a heart (period), a flower (follicular), a sun (ovulatory) and a
 * cloud (luteal), bouncing in one after another. Decorative.
 */
@Composable
fun BuddyFamily(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val c = app.cadence.core.ui.theme.CadenceTheme.cycleColors
    val face = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val members =
        listOf(
            Triple(c.menstrualPhase, c.menstrualTint, MaterialShapes.Heart),
            Triple(c.follicularPhase, c.follicularTint, MaterialShapes.Flower),
            Triple(c.ovulatoryPhase, c.ovulatoryTint, MaterialShapes.Sunny),
            Triple(c.lutealPhase, c.lutealTint, MaterialShapes.Puffy),
        )
    androidx.compose.foundation.layout.Row(
        modifier,
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(size / 6),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        members.forEachIndexed { i, (color, tint, shape) ->
            Buddy(
                body =
                    androidx.compose.ui.graphics
                        .lerp(tint, color, FAMILY_DEPTH),
                face = face,
                shape = shape,
                expression = if (i % 2 == 0) BuddyFace.HAPPY else BuddyFace.JOYFUL,
                size = size,
                enterDelayMillis = i * FAMILY_STAGGER_MS,
                // A gentle wave: the middle two sit a little higher.
                modifier =
                    androidx.compose.ui.Modifier
                        .padding(bottom = if (i == 1 || i == 2) size / 4 else 0.dp),
            )
        }
    }
}

private const val FAMILY_DEPTH = 0.3f
private const val FAMILY_STAGGER_MS = 120L
