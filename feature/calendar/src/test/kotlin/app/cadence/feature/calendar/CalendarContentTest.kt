package app.cadence.feature.calendar

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
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

    private fun show(
        actions: CalendarActions = CalendarActions(),
        selected: LocalDate = today,
        entry: DayEntry = DayEntry(selected),
    ) {
        val prediction = CyclePredictor.predict(CycleHistory.from(days), CycleDefaults(28, 5), today)
        compose.setContent {
            CadenceTheme {
                CalendarContent(
                    CalendarUiState.Ready(
                        today,
                        days,
                        prediction,
                        detailDates = setOf(LocalDate.of(2026, 3, 22)),
                        lutealPhaseLength = 14,
                        selected = selected,
                        selectedEntry = entry,
                    ),
                    actions,
                )
            }
        }
    }

    private fun day(description: String) = compose.onNode(hasContentDescription(description, substring = true))

    private fun hasState(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    private fun button(text: String) = compose.onNodeWithText(text).performScrollTo()

    @Test
    fun `predicted, today and logged days are described for TalkBack`() {
        show()

        day("March 29, 2026, predicted period").assertExists()
        day("March 20, 2026, today").assertIsSelected()
        day("March 22, 2026, has a log").assertExists()

        compose.onNodeWithText("Edit period days").performScrollTo().performClick()
        compose
            .onNode(
                hasContentDescription("March 3, 2026", substring = true) and hasState("Period day"),
            ).assertExists()
        compose
            .onNode(hasContentDescription("March 10, 2026", substring = true) and hasState("Not a period day"))
            .assertExists()
    }

    @Test
    fun `tapping a day selects it, including future days`() {
        var selected: LocalDate? = null
        var toggled: LocalDate? = null
        var opened: LocalDate? = null
        show(CalendarActions(onSelect = { selected = it }, onToggle = { toggled = it }, onOpenLog = { opened = it }))

        day("March 10, 2026").performClick()
        assertEquals(LocalDate.of(2026, 3, 10), selected)

        day("March 25, 2026").performClick()
        assertEquals(LocalDate.of(2026, 3, 25), selected)
        assertEquals(null, toggled)
        assertEquals(null, opened)
    }

    @Test
    fun `in edit mode tapping a day toggles it, and future days are off limits`() {
        var selected: LocalDate? = null
        var toggled: LocalDate? = null
        show(CalendarActions(onSelect = { selected = it }, onToggle = { toggled = it }))

        compose.onNodeWithText("Edit period days").performScrollTo().performClick()
        day("March 10, 2026").performClick()

        assertEquals(LocalDate.of(2026, 3, 10), toggled)
        assertEquals(null, selected)
        day("March 25, 2026").assertIsNotEnabled()
    }

    @Test
    fun `the selected day shows where it is in the cycle and what was logged`() {
        val date = LocalDate.of(2026, 3, 3)
        show(
            selected = date,
            entry =
                DayEntry(
                    date,
                    flow = MenstrualFlow.HEAVY,
                    symptoms = mapOf("cramps" to Severity.MODERATE),
                    moods = setOf("calm"),
                    energy = 2,
                    note = "Rested all afternoon",
                ),
        )

        compose.onNodeWithText("Tuesday, March 3").assertExists()
        compose.onNodeWithText("Period · Cycle day 3").assertExists()
        compose.onNodeWithText("Heavy flow").assertExists()
        compose.onNodeWithText("Cramps (moderate)").assertExists()
        compose.onNodeWithText("Calm").assertExists()
        compose.onNodeWithText("Energy 2/5").assertExists()
        compose.onNodeWithText("Rested all afternoon").assertExists()
        button("Edit details").assertExists()
    }

    @Test
    fun `a past day can start a period, or open its log`() {
        var started: LocalDate? = null
        var opened: LocalDate? = null
        val date = LocalDate.of(2026, 2, 3)
        show(CalendarActions(onStartPeriod = { started = it }, onOpenLog = { opened = it }), selected = date)

        compose.onNodeWithText("Nothing logged for this day yet.").assertExists()
        button("Period started").performClick()
        assertEquals(date, started)
        button("Add details").performClick()
        assertEquals(date, opened)
        compose.onNodeWithText("Period ended").assertDoesNotExist()
    }

    @Test
    fun `a period day can end the period there or stop being a period day`() {
        var ended: LocalDate? = null
        var removed: LocalDate? = null
        val date = LocalDate.of(2026, 3, 3)
        show(CalendarActions(onEndPeriod = { ended = it }, onRemovePeriodDay = { removed = it }), selected = date)

        compose.onNodeWithText("Period started").assertDoesNotExist()
        button("Period ended").performClick()
        assertEquals(date, ended)
        button("Not a period day").performClick()
        assertEquals(date, removed)
    }

    @Test
    fun `the last day of a period can't end it again`() {
        show(selected = LocalDate.of(2026, 3, 5))
        compose.onNodeWithText("Period ended").assertDoesNotExist()
        button("Not a period day").assertExists()
    }

    @Test
    fun `a future day shows its prediction and can't be logged yet`() {
        val date = LocalDate.of(2026, 3, 30)
        show(selected = date)

        compose.onNodeWithText("Predicted period · Cycle day 2 (estimate)").assertExists()
        compose.onNodeWithText("This day hasn't come yet", substring = true).assertExists()
        compose.onNodeWithText("Add details").assertDoesNotExist()
        compose.onNodeWithText("Period started").assertDoesNotExist()
    }

    @Test
    fun `fertile window and ovulation are described as estimates`() {
        show()
        day("April 12, 2026, estimated ovulation").assertExists()
        day("April 10, 2026, estimated fertile window").assertExists()
        compose
            .onNodeWithText(
                "Fertile window and ovulation are estimates",
                substring = true,
            ).performScrollTo()
            .assertExists()
    }
}
