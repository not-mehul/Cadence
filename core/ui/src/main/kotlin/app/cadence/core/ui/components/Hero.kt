package app.cadence.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cadence.core.ui.expressive.sparkles
import app.cadence.core.ui.theme.CadenceTheme
import kotlin.math.max

/**
 * Cadence's motif: quiet concentric ripples, like a rhythm spreading out. Drawn from
 * [centerFraction] of the area, in [color] at very low opacity so text stays easy to read.
 */
fun DrawScope.drawRipples(
    color: Color,
    centerFraction: Offset = Offset(0.5f, 0.32f),
    rings: Int = 5,
) {
    val centre = Offset(size.width * centerFraction.x, size.height * centerFraction.y)
    val step = max(size.width, size.height) / (rings + 1.5f)
    repeat(rings) { i ->
        drawCircle(
            color = color.copy(alpha = RIPPLE_ALPHA * (rings - i) / rings),
            radius = step * (i + 1.2f),
            center = centre,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

private const val RIPPLE_ALPHA = 0.16f

private const val SPARKLE_ALPHA = 0.32f

/**
 * The panel at the top of a screen: a soft wash of [style]'s tint fading into the card
 * surface, with the ripple motif behind the content.
 */
@Composable
fun HeroCard(
    style: PhaseStyle,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.verticalGradient(0f to style.tint, 1f to lerpTowards(style.tint, base)))
            .drawBehind { drawRipples(style.color) }
            .sparkles(style.color, alpha = SPARKLE_ALPHA),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(
                    horizontal = CadenceTheme.spacing.lg,
                    vertical = CadenceTheme.spacing.xl,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
            content = content,
        )
    }
}

/** Two thirds of the way from [from] to [to]: the wash fades but never disappears, so the panel keeps its edge. */
private fun lerpTowards(
    from: Color,
    to: Color,
): Color =
    androidx.compose.ui.graphics
        .lerp(from, to, 0.66f)

/** A screen's large title, in the display typeface. */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** Rows grouped in one card, the way related settings sit together. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CadenceSurface(modifier) {
        Column(Modifier.padding(vertical = CadenceTheme.spacing.xs), content = content)
    }
}
