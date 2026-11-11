package app.cadence

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CycleInsights
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
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
import app.cadence.feature.widget.WidgetContent
import app.cadence.feature.widget.WidgetText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import app.cadence.core.ui.R as UiR
import app.cadence.feature.widget.R as WidgetR

/**
 * Russian: the plural forms agree with their numbers, dates that stand alone start with a capital,
 * and the main screens keep their key content reachable on a small phone, also at 200% text.
 * Lint (MissingTranslation, MissingQuantity) checks that nothing is left untranslated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "ru-rRU-w360dp-h740dp")
class RussianTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val russian = Locale.forLanguageTag("ru-RU")
    private val today = LocalDate.of(2026, 3, 20)
    private val flows =
        (0L until 3L)
            .flatMap { cycle -> (0L until 5L).map { LocalDate.of(2026, 1, 1).plusDays(cycle * 28 + it) } }
            .associateWith { MenstrualFlow.MEDIUM }
    private val settings = UserSettings(cycleDefaults = CycleDefaults(28, 5), onboardingComplete = true)

    private fun days(count: Int) = context.resources.getQuantityString(UiR.plurals.days_count, count, count)

    @Test
    fun `day counts take the right ending`() {
        assertEquals("1 день", days(1))
        assertEquals("2 дня", days(2))
        assertEquals("4 дня", days(4))
        assertEquals("5 дней", days(5))
        assertEquals("11 дней", days(11))
        assertEquals("14 дней", days(14))
        assertEquals("21 день", days(21))
        assertEquals("22 дня", days(22))
        assertEquals("28 дней", days(28))
        assertEquals("101 день", days(101))
    }

    @Test
    fun `a count ending in one is never worded as tomorrow`() {
        val inDays = { count: Int ->
            context.resources.getQuantityString(WidgetR.plurals.widget_break_in, count, count)
        }
        assertEquals("Перерыв через 1 день", inDays(1))
        assertEquals("Перерыв через 21 день", inDays(21))
    }

    @Test
    fun `the widget reads naturally, with the date capitalised`() {
        val text = { s: UserSettings, day: LocalDate ->
            WidgetContent.build(context, s, CycleSnapshot.compute(flows, s, day), russian)
        }
        assertEquals(WidgetText("День 23", "Пятница, 20 марта"), text(settings, today))
        val detailed = settings.copy(reminders = ReminderSettings(discreet = false))
        assertEquals(WidgetText("Месячные через 6 дней", "День цикла: 23"), text(detailed, today))
        assertEquals("Задержка 3 дня", text(detailed, LocalDate.of(2026, 3, 29)).primary)
        assertEquals("Задержка 6 дней", text(detailed, LocalDate.of(2026, 4, 1)).primary)
    }

    private fun show(content: @Composable () -> Unit) = compose.setContent { CadenceTheme { content() } }

    private fun reachable(text: String) {
        compose.onNodeWithText(text, substring = true).performScrollTo().assertExists()
    }

    private fun showToday() =
        show {
            TodayContent(
                TodayUiState.Ready(CycleSnapshot.compute(flows, settings, today), Goal.TRACK, 5, DayEntry(today)),
                {},
                {},
                {},
                {},
            )
        }

    private fun showCalendar() =
        show {
            val prediction = CyclePredictor.predict(CycleHistory.from(flows), settings.cycleDefaults, today)
            CalendarContent(CalendarUiState.Ready(today, flows, prediction, emptySet(), 14), CalendarActions())
        }

    private fun showInsights() =
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

    private fun showSettings() =
        show {
            SettingsContent(
                settings,
                SettingsActions({}, {}, {}, {}, {}, {}),
                tracking = TrackingSettingsState(today),
            )
        }

    @Test
    fun today() {
        showToday()
        compose.onNodeWithText("Пятница, 20 марта").assertExists()
        compose.onNodeWithText("Месячные через 6 дней").assertExists()
        reachable("Записи дня")
    }

    @Test
    fun calendar() {
        showCalendar()
        compose.onNodeWithText("Март 2026", substring = true).assertExists()
        reachable("Изменить дни месячных")
    }

    @Test
    fun insights() {
        showInsights()
        reachable("Ваши циклы")
    }

    @Test
    fun settings() {
        showSettings()
        reachable("Язык")
        reachable("Удалить все данные")
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `today at 200 percent text`() {
        showToday()
        reachable("Записи дня")
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `calendar at 200 percent text`() {
        showCalendar()
        reachable("Изменить дни месячных")
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `insights at 200 percent text`() {
        showInsights()
        reachable("Ваши циклы")
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `settings at 200 percent text`() {
        showSettings()
        reachable("Удалить все данные")
    }
}
