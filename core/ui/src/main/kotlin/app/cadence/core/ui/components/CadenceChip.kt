package app.cadence.core.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.cadence.core.ui.theme.CadenceTheme

/** A toggleable chip, used for symptoms, moods and other quick log options. */
@Composable
fun CadenceChip(
    label: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        label = { Text(label) },
        modifier = modifier.semantics { role = Role.Checkbox },
        enabled = enabled,
    )
}

@Preview
@Composable
private fun CadenceChipPreview() {
    CadenceTheme { CadenceChip(label = "Cramps", selected = true, onToggle = {}) }
}
