package app.cadence.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.patterns.HeadsUp
import app.cadence.core.domain.patterns.PatternTiming
import app.cadence.core.domain.patterns.SymptomPattern
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(qualifiers = "en-rGB")
class TodayContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val march1 = LocalDate.of(2026, 3, 1)
    private val period = (0L until 5L).associate { march1.plusDays(it) to MenstrualFlow.MEDIUM }

    private fun show(
        today: LocalDate,
        days: Map<LocalDate, MenstrualFlow> = period,
        goal: Goal = Goal.TRACK,
        onStart: () -> Unit = {},
        onEnd: (LocalDate) -> Unit = {},
        entry: DayEntry? = null,
        onOpenLog: () -> Unit = {},
    ) {
        val settings = UserSettings(goal = goal, cycleDefaults = CycleDefaults(28, 5), onboardingComplete = true)
        val state = TodayUiState.Ready(CycleSnapshot.compute(days, settings, today), goal, 5, entry ?: DayEntry(today))
        compose.setContent {
            CadenceTheme {
                TodayContent(
                    state,
                    onStartPeriod = onStart,
                    onEndPeriod = onEnd,
                    onLogToday = {},
                    onOpenLog = onOpenLog,
                )
            }
        }
    }

    @Test
    fun `a heads-up groups what tends to come around now and tomorrow`() {
        val settings = UserSettings(cycleDefaults = CycleDefaults(28, 5), onboardingComplete = true)
        val state =
            TodayUiState.Ready(
                CycleSnapshot.compute(period, settings, march1),
                Goal.TRACK,
                5,
                DayEntry(march1),
                tagLabels = mapOf(4L to "Travel"),
                headsUp =
                    listOf(
                        HeadsUp(SymptomPattern("cramps", PatternTiming.DURING_PERIOD, null, 3, 3), 0),
                        HeadsUp(SymptomPattern("tag:4", PatternTiming.BEFORE_PERIOD, 2, 3, 4), 1),
                    ),
            )
        compose.setContent { CadenceTheme { TodayContent(state, {}, {}, {}, {}) } }

        compose.onNodeWithText("Heads-up").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Around now").assertExists()
        compose.onNodeWithText("Cramps").assertExists()
        compose.onNodeWithText("Tomorrow").assertExists()
        compose.onNodeWithText("Travel").assertExists()
    }

    @Test
    fun `no data invites the first log`() {
        var started = false
        show(today = march1, days = emptyMap(), onStart = { started = true })

        compose.onNodeWithText("Log your period to get started").assertIsDisplayed()
        compose.onNodeWithText("Period started").performScrollTo().performClick()

        assertEquals(true, started)
    }

    @Test
    fun `between periods shows the countdown, ring and phase`() {
        show(today = LocalDate.of(2026, 3, 20))

        compose.onNodeWithText("Period in 9 days").assertIsDisplayed()
        compose.onNode(hasContentDescription("Cycle day 20 of about 28. Luteal phase.")).assertExists()
        compose.onNodeWithText("Based on your typical cycle", substring = true).assertExists()
    }

    @Test
    fun `ending a period yesterday`() {
        var ended: LocalDate? = null
        show(today = LocalDate.of(2026, 3, 3), onEnd = { ended = it })

        compose.onNodeWithText("Day 3 of your period").assertIsDisplayed()
        compose.onNodeWithText("Period ended").performScrollTo().performClick()
        compose.onNodeWithText("When did your period end?").assertExists()
        compose.onNodeWithText("Yesterday").performClick()

        assertEquals(LocalDate.of(2026, 3, 2), ended)
    }

    @Test
    fun `late period`() {
        show(today = LocalDate.of(2026, 4, 2))

        compose.onNodeWithText("Period 4 days late").assertIsDisplayed()
        compose.onNodeWithText("Running late").assertExists()
    }

    @Test
    fun `birth control mode explains why phases are hidden`() {
        show(today = LocalDate.of(2026, 3, 20), goal = Goal.BIRTH_CONTROL)

        compose.onNodeWithText("Hormonal birth control").assertExists()
        compose.onNode(hasContentDescription("Cycle day 20 of about 28.")).assertExists()
    }

    @Test
    fun `today's log card summarises the entry and opens the log`() {
        var opened = false
        val today = LocalDate.of(2026, 3, 20)
        val entry =
            DayEntry(
                today,
                symptoms = mapOf("cramps" to Severity.MODERATE),
                moods = setOf("calm"),
                painLevel = 6,
                note = "x",
            )
        show(today = today, entry = entry, onOpenLog = { opened = true })

        compose.onNodeWithText("Cramps (moderate)").performScrollTo().assertExists()
        compose.onNodeWithText("Calm").assertExists()
        compose.onNodeWithText("Pain 6/10").assertExists()
        compose.onNodeWithText("Note").assertExists()
        compose.onNodeWithText("Edit").performScrollTo().performClick()

        assertEquals(true, opened)
    }

    @Test
    fun `empty log invites logging today`() {
        show(today = LocalDate.of(2026, 3, 20))
        compose.onNodeWithText("Log today").performScrollTo().assertExists()
    }

    @Test
    fun `due within the likely range`() {
        show(today = LocalDate.of(2026, 3, 31))
        compose.onNodeWithText("Period due any day now").assertIsDisplayed()
        compose.onNodeWithText("Due any day").performScrollTo().assertExists()
    }

    @Test
    fun `likely range and fertile window with its disclaimer`() {
        show(today = LocalDate.of(2026, 3, 8))
        compose.onNodeWithText("Likely between 26 Mar and 1 Apr", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Estimated fertile window 10 Mar to 16 Mar").performScrollTo().assertExists()
        compose.onNodeWithText("can't be used to prevent pregnancy", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `no fertile window on birth control`() {
        show(today = LocalDate.of(2026, 3, 8), goal = Goal.BIRTH_CONTROL)
        compose.onNodeWithText("Estimated fertile window", substring = true).assertDoesNotExist()
    }
}
