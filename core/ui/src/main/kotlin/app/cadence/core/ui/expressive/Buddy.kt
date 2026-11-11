package app.cadence.core.ui.expressive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

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
 * Cadence's little companion: a soft Material shape with a face. It bounces in and grows from a
 * circle into its [shape], then breathes gently and blinks every few seconds. Tap it and it hops
 * with a happy face. Purely decorative: it has no semantics, so screen readers skip it, and it
 * keeps still when the system's animations are off.
 *
 * Buddies on one screen pass different [rhythm]s so they don't blink in unison.
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
    rhythm: Int = 0,
) {
    val animate = rememberAnimationsEnabled()
    val grow = remember { Animatable(if (animate) 0f else 1f) }
    val bounce = remember { Animatable(if (animate) 0.6f else 1f) }
    val hop = remember { Animatable(0f) }
    var cheering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val morph = remember(shape) { Morph(MaterialShapes.Circle, shape) }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        delay(enterDelayMillis)
        launch { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
        bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow))
    }
    val idle = if (animate) rememberIdle(rhythm) else null

    val tap =
        if (animate) {
            Modifier.pointerInput(Unit) {
                detectTapGestures {
                    scope.launch {
                        cheering = true
                        hop.animateTo(1f, tween(HOP_UP_MS, easing = FastOutSlowInEasing))
                        hop.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMedium))
                        delay(CHEER_HOLD_MS)
                        cheering = false
                    }
                }
            }
        } else {
            Modifier
        }

    Canvas(modifier.size(size).then(tap)) {
        val breath = idle?.breath?.value ?: 1f
        translate(top = -hop.value * this.size.height * HOP_HEIGHT) {
            scale(bounce.value * breath) {
                drawPath(morph.scaledPath(grow.value, this.size), body)
                drawFace(
                    face,
                    cheeks,
                    if (cheering) BuddyFace.JOYFUL else expression,
                    idle?.eyes?.value ?: 1f,
                )
            }
        }
    }
}

/** The buddy's idle loop: a slow breath and a blink every few seconds. */
private class Idle(
    val breath: State<Float>,
    val eyes: State<Float>,
)

@Composable
private fun rememberIdle(rhythm: Int): Idle {
    val transition = rememberInfiniteTransition(label = "buddy")
    val offset = StartOffset((rhythm * RHYTHM_STEP_MS) % BLINK_CYCLE_MS)
    val breath =
        transition.animateFloat(
            initialValue = 1f,
            targetValue = BREATH_SCALE,
            animationSpec =
                infiniteRepeatable(
                    tween(BREATH_MS + rhythm % 3 * 200, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    offset,
                ),
            label = "breath",
        )
    val eyes =
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    keyframes {
                        durationMillis = BLINK_CYCLE_MS
                        1f at BLINK_AT_MS
                        EYES_SHUT at BLINK_AT_MS + BLINK_MS
                        1f at BLINK_AT_MS + BLINK_MS * 2
                    },
                    initialStartOffset = offset,
                ),
            label = "blink",
        )
    return remember(breath, eyes) { Idle(breath, eyes) }
}

private const val BREATH_SCALE = 1.035f
private const val BREATH_MS = 1_800
private const val BLINK_CYCLE_MS = 4_200
private const val BLINK_AT_MS = 3_300
private const val BLINK_MS = 90
private const val EYES_SHUT = 0.1f
private const val RHYTHM_STEP_MS = 1_300
private const val HOP_UP_MS = 140
private const val HOP_HEIGHT = 0.18f
private const val CHEER_HOLD_MS = 700L

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
                rhythm = i,
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

/**
 * Three little "z"s drifting up and fading, for a sleeping buddy. Decorative: hidden from screen
 * readers. They hold still, softly visible, when animations are off.
 */
@Composable
fun SleepyZs(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val drift =
        if (rememberAnimationsEnabled()) {
            rememberInfiniteTransition(label = "zzz").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(ZZZ_MS, easing = LinearEasing)),
                label = "drift",
            )
        } else {
            null
        }
    androidx.compose.foundation.layout.Box(modifier.size(ZZZ_BOX).clearAndSetSemantics {}) {
        repeat(ZZZ_COUNT) { i ->
            androidx.compose.material3.Text(
                text = "z",
                color = color,
                fontSize = (12 + i * 5).sp,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .graphicsLayer {
                            // Each z is a third of a cycle behind the one before.
                            val p = drift?.let { (it.value + i / ZZZ_COUNT.toFloat()) % 1f } ?: (0.3f + i * 0.2f)
                            translationX = (i * 14).dp.toPx() + 4.dp.toPx() * sin(p * 2 * PI).toFloat()
                            translationY = -ZZZ_RISE.toPx() * p
                            alpha = sin(p * PI).toFloat()
                        },
            )
        }
    }
}

private const val ZZZ_MS = 3_600
private const val ZZZ_COUNT = 3
private val ZZZ_BOX = 64.dp
private val ZZZ_RISE = 40.dp
