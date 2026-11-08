package app.cadence.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.cadence.core.ui.theme.CadenceTheme

/** A section title, announced as a heading by TalkBack. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .semantics { heading() }
                .padding(vertical = CadenceTheme.spacing.sm),
    )
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    CadenceTheme { SectionHeader("Reminders") }
}
