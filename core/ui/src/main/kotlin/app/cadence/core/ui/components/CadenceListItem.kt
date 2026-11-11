package app.cadence.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * A row in a grouped card (settings, backup, Health Connect): a headline, optional supporting
 * text, and an optional decorative [icon] on a soft cookie badge. Interaction (click, toggle) goes
 * on [modifier] so each caller keeps its own semantics.
 */
@Composable
fun CadenceListItem(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        leadingContent =
            icon?.let { res ->
                { IconDisc(painterResource(res), iconTint, iconContainer, size = 40.dp) }
            },
        trailingContent = trailing,
        supportingContent = supporting,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        content = headline,
    )
}
