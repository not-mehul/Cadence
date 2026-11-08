package app.cadence.feature.log

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.log.Severity
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w411dp-h1600dp")
class DayLogContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 20)
    private var entry = DayEntry(today)

    private fun show(hidden: Set<LogCategory> = emptySet()) {
        compose.setContent {
            CadenceTheme {
                DayLogContent(
                    state = DayLogUiState.Ready(entry, isToday = true, hiddenCategories = hidden),
                    onEdit = { transform -> entry = transform(entry) },
                    onNoteChange = {},
                    onDone = {},
                )
            }
        }
    }

    @Test
    fun `every category is shown by default`() {
        show()
        listOf("Flow", "Symptoms", "Mood", "Pain", "Energy", "Sleep quality", "Stress", "Sex", "Notes").forEach {
            compose.onNodeWithText(it).performScrollTo().assertExists()
        }
    }

    @Test
    fun `hidden categories are left out`() {
        show(hidden = setOf(LogCategory.SEX, LogCategory.STRESS))
        compose.onNodeWithText("Sex").assertDoesNotExist()
        compose.onNodeWithText("Stress").assertDoesNotExist()
        compose.onNodeWithText("Mood").performScrollTo().assertExists()
    }

    @Test
    fun `tapping a symptom cycles it`() {
        show()
        compose.onNodeWithText("Cramps").performScrollTo().performClick()
        assertEquals(mapOf("cramps" to Severity.MILD), entry.symptoms)
    }

    @Test
    fun `picking flow, mood and a rating edits the entry`() {
        show()
        compose.onNodeWithText("Heavy").performScrollTo().performClick()
        compose.onNodeWithText("Calm").performScrollTo().performClick()
        compose.onNode(hasContentDescription("Energy, 4 of 5")).performScrollTo().performClick()

        assertEquals(app.cadence.core.domain.model.MenstrualFlow.HEAVY, entry.flow)
        assertEquals(setOf("calm"), entry.moods)
        assertEquals(4, entry.energy)
    }

    @Test
    fun `tapping the selected pain level clears it`() {
        entry = DayEntry(today, painLevel = 5)
        show()
        compose.onNode(hasContentDescription("Pain, 5 of 10")).performScrollTo().performClick()
        assertEquals(null, entry.painLevel)
    }
}
