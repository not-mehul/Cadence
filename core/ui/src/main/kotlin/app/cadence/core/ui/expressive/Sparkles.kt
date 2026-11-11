package app.cadence.core.ui.expressive

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin

/**
 * A scattering of four-point sparkles and dots behind content, like stickers on a page. Each
 * sparkle twinkles softly with [phase] (0 to 1, see [rememberTwinkle]); positions are fixed
 * fractions so it looks the same every time.
 */
fun Modifier.sparkles(
    color: Color,
    alpha: Float = 0.5f,
    phase: () -> Float = { 0f },
): Modifier =
    drawBehind {
        val t = phase()
        SPARKLES.forEachIndexed { i, (x, y, r) ->
            // Each sparkle is a little out of step with the others.
            val wave = 0.5f + 0.5f * sin(2 * PI * (t + i * TWINKLE_STEP)).toFloat()
            val glow = TWINKLE_FLOOR + (1f - TWINKLE_FLOOR) * wave
            drawSparkle(
                Offset(size.width * x, size.height * y),
                r * density * (0.85f + 0.15f * glow),
                color,
                alpha * glow,
            )
        }
        DOTS.forEach { (x, y, r) ->
            drawCircle(color.copy(alpha = alpha * 0.7f), r * density, Offset(size.width * x, size.height * y))
        }
    }

private fun DrawScope.drawSparkle(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
) {
    // Four points joined by inward curves.
    val inner = radius * 0.28f
    val path =
        Path().apply {
            moveTo(center.x, center.y - radius)
            quadraticTo(center.x + inner, center.y - inner, center.x + radius, center.y)
            quadraticTo(center.x + inner, center.y + inner, center.x, center.y + radius)
            quadraticTo(center.x - inner, center.y + inner, center.x - radius, center.y)
            quadraticTo(center.x - inner, center.y - inner, center.x, center.y - radius)
            close()
        }
    drawPath(path, color.copy(alpha = alpha))
}

/** A slow 0-to-1 loop for [sparkles]; null when animations are off, so they hold still. */
@Composable
fun rememberTwinkle(): State<Float>? {
    if (!rememberAnimationsEnabled()) return null
    return rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TWINKLE_MS, easing = LinearEasing)),
        label = "twinkle",
    )
}

private const val TWINKLE_MS = 6_000
private const val TWINKLE_FLOOR = 0.35f
private const val TWINKLE_STEP = 0.27f

/** x and y as fractions of the size, radius in dp. Kept to the upper edges, clear of centred text. */
private val SPARKLES =
    listOf(
        Triple(0.08f, 0.1f, 7f),
        Triple(0.91f, 0.08f, 9f),
        Triple(0.95f, 0.3f, 5f),
        Triple(0.05f, 0.34f, 5f),
    )

private val DOTS =
    listOf(
        Triple(0.18f, 0.05f, 2.5f),
        Triple(0.8f, 0.15f, 2f),
        Triple(0.04f, 0.2f, 2.5f),
        Triple(0.97f, 0.19f, 2f),
    )
