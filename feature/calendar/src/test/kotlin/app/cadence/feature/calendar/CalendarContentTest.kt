package app.cadence.feature.calendar

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class CalendarContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 20)
    private val days = (1..5).associate { LocalDate.of(2026, 3, it) to MenstrualFlow.MEDIUM }

    private fun show(onToggle: (LocalDate) -> Unit = {}) {
        val prediction = CyclePredictor.predict(CycleHistory.from(days), CycleDefaults(28, 5), today)
        compose.setContent {
            CadenceTheme { CalendarContent(CalendarUiState.Ready(today, days, prediction), onToggle = onToggle) }
        }
    }

    private fun day(description: String) = compose.onNode(hasContentDescription(description, substring = true))

    private fun hasState(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    @Test
    fun `logged, predicted and today are described for TalkBack`() {
        show()

        compose
            .onNode(
                hasContentDescription("March 3, 2026", substring = true) and hasState("Period day"),
            ).assertExists()
        compose
            .onNode(
                hasContentDescription("March 10, 2026", substring = true) and hasState("Not a period day"),
            ).assertExists()
        day("March 29, 2026, predicted period").assertExists()
        day("March 20, 2026, today").assertExists()
    }

    @Test
    fun `tapping a day toggles it`() {
        var toggled: LocalDate? = null
        show(onToggle = { toggled = it })

        day("March 10, 2026").performClick()

        assertEquals(LocalDate.of(2026, 3, 10), toggled)
    }

    @Test
    fun `future days without a log are disabled`() {
        show()
        day("March 25, 2026").assertIsNotEnabled()
    }
}
