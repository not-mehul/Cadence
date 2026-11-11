package app.cadence.core.ui.expressive

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * A scattering of four-point sparkles and dots behind content, like stickers on a page.
 * Static, so it never distracts; positions are fixed fractions so it looks the same every time.
 */
fun Modifier.sparkles(
    color: Color,
    alpha: Float = 0.5f,
): Modifier =
    drawBehind {
        SPARKLES.forEach { (x, y, r) ->
            drawSparkle(Offset(size.width * x, size.height * y), r * density, color, alpha)
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
