package app.cadence.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.cadence.core.ui.components.PlaceholderScreen
import app.cadence.core.ui.theme.CadenceTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.settings_title),
        description = stringResource(R.string.settings_placeholder),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    CadenceTheme { SettingsScreen() }
}
