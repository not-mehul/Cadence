package app.cadence.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cadence.core.ui.R
import app.cadence.core.ui.theme.CadenceTheme

/**
 * Picks one number on a scale, or none. Tapping the selected number clears it, so "not
 * logged" is always reachable. [label] names the scale for TalkBack ("Energy, 3 of 5").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleSelector(
    range: IntRange,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    lowLabel: String? = null,
    highLabel: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm),
        ) {
            range.forEach { value ->
                val isSelected = value == selected
                val description = stringResource(R.string.scale_value, label, value, range.last)
                val colors = MaterialTheme.colorScheme
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) colors.primary else colors.surface,
                    contentColor = if (isSelected) colors.onPrimary else colors.onSurface,
                    border = if (isSelected) null else BorderStroke(1.dp, colors.outline),
                    modifier =
                        Modifier
                            .size(CadenceTheme.spacing.minTouchTarget)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(if (isSelected) null else value) },
                            ).semantics { contentDescription = description },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(value.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (lowLabel != null || highLabel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ScaleEndLabel(lowLabel.orEmpty())
                ScaleEndLabel(highLabel.orEmpty())
            }
        }
    }
}

@Composable
private fun ScaleEndLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview(showBackground = true)
@Composable
private fun ScaleSelectorPreview() {
    CadenceTheme {
        ScaleSelector(1..5, selected = 3, onSelect = {}, label = "Energy", lowLabel = "Low", highLabel = "High")
    }
}
