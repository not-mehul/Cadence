package app.cadence.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.cadence.core.ui.theme.CadenceTheme

/** A content card on a tonal surface. Used for phase info, stats and settings groups. */
@Composable
fun CadenceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(CadenceTheme.spacing.lg), content = content)
    }
}

@Preview
@Composable
private fun CadenceCardPreview() {
    CadenceTheme {
        CadenceCard {
            Text("Follicular phase", style = MaterialTheme.typography.titleMedium)
            Text("Energy often rises as estrogen increases.")
        }
    }
}
