package app.cadence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CadenceAppTest {
    @get:Rule
    val compose = createComposeRule()

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Test
    fun `starts on Today`() {
        compose.setContent { CadenceTheme { CadenceApp() } }

        tab("Today").assertIsSelected()
        compose.onNodeWithText("Your cycle day, current phase and next period will appear here.").assertIsDisplayed()
    }

    @Test
    fun `each tab shows its screen`() {
        compose.setContent { CadenceTheme { CadenceApp() } }

        val screens =
            mapOf(
                "Calendar" to "Your logged and predicted periods will appear on a monthly calendar here.",
                "Insights" to "Cycle averages, trends and patterns will appear here once you have logged a few cycles.",
                "Settings" to "Cycle defaults, reminders, privacy and data controls will appear here.",
                "Today" to "Your cycle day, current phase and next period will appear here.",
            )
        screens.forEach { (label, text) ->
            tab(label).performClick()
            tab(label).assertIsSelected()
            compose.onNodeWithText(text).assertIsDisplayed()
        }
        tab("Calendar").assertIsNotSelected()
    }
}
