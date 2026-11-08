package app.cadence.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.cadence.core.ui.components.PlaceholderScreen
import app.cadence.core.ui.theme.CadenceTheme

@Composable
fun InsightsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.insights_title),
        description = stringResource(R.string.insights_placeholder),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun InsightsScreenPreview() {
    CadenceTheme { InsightsScreen() }
}
