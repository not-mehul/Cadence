package app.cadence.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cadence.core.ui.theme.CadenceTheme

/** The surface and hairline border every Cadence card shares. */
@Composable
fun CadenceSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        content = content,
    )
}

/** A content card: crisp surface, hairline border, generous padding. */
@Composable
fun CadenceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CadenceSurface(modifier) {
        Column(modifier = Modifier.padding(CARD_PADDING), content = content)
    }
}

/**
 * A card's title, announced as a heading. An [icon] sits in a small tinted disc; it is
 * decorative (the title says the same thing), so it has no description.
 */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    accentContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = CadenceTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
    ) {
        if (icon != null) IconDisc(icon, accent, accentContainer)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing()
    }
}

/** A small tinted disc holding a decorative icon. */
@Composable
fun IconDisc(
    icon: Painter,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(36.dp).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private val CARD_PADDING = 20.dp

@Preview(showBackground = true)
@Composable
private fun CadenceCardPreview() {
    CadenceTheme {
        CadenceCard {
            CardHeader("Follicular phase")
            Text("Energy often rises as estrogen increases.")
        }
    }
}
