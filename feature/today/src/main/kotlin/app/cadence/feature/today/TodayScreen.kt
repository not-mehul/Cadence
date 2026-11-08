package app.cadence.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.cadence.core.ui.components.PlaceholderScreen
import app.cadence.core.ui.theme.CadenceTheme

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.today_title),
        description = stringResource(R.string.today_placeholder),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    CadenceTheme { TodayScreen() }
}
