package app.cadence

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CycleInsights
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.ui.theme.CadenceTheme
import app.cadence.feature.calendar.CalendarActions
import app.cadence.feature.calendar.CalendarContent
import app.cadence.feature.calendar.CalendarUiState
import app.cadence.feature.insights.CurrentCycle
import app.cadence.feature.insights.CycleRow
import app.cadence.feature.insights.InsightsContent
import app.cadence.feature.insights.InsightsUiState
import app.cadence.feature.settings.SettingsActions
import app.cadence.feature.settings.SettingsContent
import app.cadence.feature.settings.TrackingSettingsState
import app.cadence.feature.today.TodayContent
import app.cadence.feature.today.TodayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Every main screen lays out right-to-left and at 200% text size on a small phone, with its
 * key content still reachable. Screenshots of these were checked by eye.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LayoutDirectionAndScaleTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 20)
    private val flows =
        (0L until 3L)
            .flatMap { cycle -> (0L until 5L).map { LocalDate.of(2026, 1, 1).plusDays(cycle * 28 + it) } }
            .associateWith { MenstrualFlow.MEDIUM }
    private val settings = UserSettings(cycleDefaults = CycleDefaults(28, 5), onboardingComplete = true)

    private fun show(content: @Composable () -> Unit) = compose.setContent { CadenceTheme { content() } }

    private fun reachable(text: String) {
        compose.onNodeWithText(text, substring = true).performScrollTo().assertExists()
    }

    private fun today() =
        show {
            TodayContent(
                TodayUiState.Ready(
                    CycleSnapshot.compute(flows, settings, today),
                    Goal.TRACK,
                    5,
                    DayEntry(today),
                ),
                {
                },
                {},
                {},
                {},
            )
        }

    private fun calendar() =
        show {
            val p = CyclePredictor.predict(CycleHistory.from(flows), settings.cycleDefaults, today)
            CalendarContent(CalendarUiState.Ready(today, flows, p, emptySet(), 14), CalendarActions())
        }

    private fun insights() =
        show {
            val history = CycleHistory.from(flows)
            InsightsContent(
                InsightsUiState.Ready(
                    CycleInsights.stats(history, today),
                    null,
                    history.cycles.map { CycleRow(it.start, it.length, 5, false) },
                    CurrentCycle(LocalDate.of(2026, 2, 26), 23),
                    emptyList(),
                ),
                { _, _ -> },
            )
        }

    private fun settings() =
        show {
            SettingsContent(
                settings,
                SettingsActions({}, {}, {}, {}, {}, {}),
                tracking = TrackingSettingsState(today),
            )
        }

    @Test
    @Config(qualifiers = "en-rUS-ldrtl-w360dp-h740dp")
    fun `right to left`() {
        today()
        reachable("Today's log")
    }

    @Test
    @Config(qualifiers = "en-rUS-ldrtl-w360dp-h740dp")
    fun `right to left calendar`() {
        calendar()
        reachable("Edit period days")
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h740dp", fontScale = 2.0f)
    fun `today at 200 percent text`() {
        today()
        reachable("Today's log")
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h740dp", fontScale = 2.0f)
    fun `calendar at 200 percent text`() {
        calendar()
        reachable("Edit period days")
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h740dp", fontScale = 2.0f)
    fun `insights at 200 percent text`() {
        insights()
        reachable("Your cycles")
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h740dp", fontScale = 2.0f)
    fun `settings at 200 percent text`() {
        settings()
        reachable("Delete all data")
    }
}
