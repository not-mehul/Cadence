package app.cadence.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cadence.core.ui.theme.CadenceTheme

/** The main call to action on a screen, such as "Period started". One per screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        enabled = enabled,
        // Expressive: the corners squish a little while pressed.
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(painter = leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(text = text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview
@Composable
private fun PrimaryButtonPreview() {
    CadenceTheme { PrimaryButton(text = "Period started", onClick = {}) }
}
