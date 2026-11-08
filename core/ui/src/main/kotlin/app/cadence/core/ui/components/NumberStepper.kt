package app.cadence.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cadence.core.ui.R
import app.cadence.core.ui.theme.CadenceTheme

/** Picks a number of days with − and + buttons. [label] names the value for TalkBack. */
@Composable
fun DayCountStepper(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.lg),
    ) {
        FilledTonalIconButton(
            onClick = { onValueChange(value - 1) },
            enabled = value > range.first,
        ) {
            Icon(
                painterResource(R.drawable.ic_remove),
                contentDescription = stringResource(R.string.stepper_decrease, label),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.days_count, value, value),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .widthIn(min = 120.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
        FilledTonalIconButton(
            onClick = { onValueChange(value + 1) },
            enabled = value < range.last,
        ) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.stepper_increase, label),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DayCountStepperPreview() {
    CadenceTheme { DayCountStepper(value = 28, range = 15..60, onValueChange = {}, label = "cycle length") }
}
