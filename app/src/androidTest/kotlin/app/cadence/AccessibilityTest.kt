package app.cadence

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CycleInsights
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.patterns.PatternTiming
import app.cadence.core.domain.patterns.SymptomPattern
import app.cadence.core.ui.theme.CadenceTheme
import app.cadence.feature.calendar.CalendarContent
import app.cadence.feature.calendar.CalendarUiState
import app.cadence.feature.insights.CurrentCycle
import app.cadence.feature.insights.CycleRow
import app.cadence.feature.insights.InsightsContent
import app.cadence.feature.insights.InsightsUiState
import app.cadence.feature.log.DayLogContent
import app.cadence.feature.log.DayLogUiState
import app.cadence.feature.log.LoggedMedication
import app.cadence.feature.onboarding.OnboardingActions
import app.cadence.feature.onboarding.OnboardingContent
import app.cadence.feature.onboarding.OnboardingState
import app.cadence.feature.onboarding.OnboardingStep
import app.cadence.feature.settings.SettingsActions
import app.cadence.feature.settings.SettingsContent
import app.cadence.feature.settings.TrackingSettingsState
import app.cadence.feature.today.MedicationToday
import app.cadence.feature.today.TodayContent
import app.cadence.feature.today.TodayUiState
import app.cadence.feature.today.TodayViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Accessibility Test Framework checks (touch target size, labels, text contrast, duplicate
 * clickables...) on every main screen, in both themes (docs/PLAN.md Phase 7). These need a
 * real device: under Robolectric the checks silently pass.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 20)
    private val flows =
        (0L until 4L)
            .flatMap { cycle -> (0L until 5L).map { LocalDate.of(2025, 12, 1).plusDays(cycle * 28 + it) } }
            .associateWith { MenstrualFlow.MEDIUM }
    private val tracking = UserSettings(cycleDefaults = CycleDefaults(28, 5), onboardingComplete = true)
    private val pill =
        ContraceptiveSchedule
            .typical(
                ContraceptiveMethod.PILL,
                LocalDate.of(2026, 3, 2),
            ).copy(since = LocalDate.of(2026, 3, 15))
    private val onPill = tracking.copy(goal = Goal.BIRTH_CONTROL, contraception = pill)

    // One setContent per test: the rule allows it once.
    private fun checkTheme(
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent { CadenceTheme(darkTheme = dark) { content() } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Composable
    private fun TodayTracking() {
        val state =
            TodayUiState.Ready(
                CycleSnapshot.compute(flows, tracking, today),
                Goal.TRACK,
                5,
                DayEntry(today, symptoms = mapOf("cramps" to Severity.MILD), moods = setOf("calm")),
            )
        TodayContent(state, {}, {}, {}, {})
    }

    @Composable
    private fun TodayOnPill() {
        val doses = mapOf(LocalDate.of(2026, 3, 15) to DoseStatus.TAKEN)
        val state =
            TodayUiState.Ready(
                CycleSnapshot.compute(flows, onPill, today),
                Goal.BIRTH_CONTROL,
                5,
                DayEntry(today),
                TodayViewModel.birthControlToday(pill, doses, today),
                listOf(MedicationToday(Medication(1, "Iron", MedicationUse.DAILY), taken = true)),
            )
        TodayContent(state, {}, {}, {}, {})
    }

    @Composable
    private fun Calendar() {
        val prediction = CyclePredictor.predict(CycleHistory.from(flows), tracking.cycleDefaults, today)
        CalendarContent(CalendarUiState.Ready(today, flows, prediction, setOf(today), 14), {}, {})
    }

    @Composable
    private fun Insights() {
        val history = CycleHistory.from(flows)
        val state =
            InsightsUiState.Ready(
                CycleInsights.stats(history, today),
                CyclePredictor.predict(history, tracking.cycleDefaults, today),
                history.cycles.map { CycleRow(it.start, it.length, 5, it.excluded) },
                CurrentCycle(LocalDate.of(2026, 2, 23), 26),
                emptyList(),
                listOf(SymptomPattern("cramps", PatternTiming.DURING_PERIOD, null, 3, 3)),
            )
        InsightsContent(state, { _, _ -> })
    }

    @Composable
    private fun Settings() {
        SettingsContent(
            onPill,
            SettingsActions({}, {}, {}, {}, {}, {}),
            tracking =
                TrackingSettingsState(
                    today,
                    listOf(Medication(1, "Iron", MedicationUse.DAILY)),
                    listOf(CustomTag(1, "Travel")),
                ),
        )
    }

    @Composable
    private fun Log() {
        val state =
            DayLogUiState.Ready(
                DayEntry(today, flow = MenstrualFlow.LIGHT, moods = setOf("calm"), painLevel = 3),
                isToday = true,
                hiddenCategories = emptySet(),
                medications = listOf(LoggedMedication(Medication(1, "Iron", MedicationUse.DAILY), taken = false)),
                tags = listOf(CustomTag(1, "Travel")),
            )
        DayLogContent(state, {}, {}, {})
    }

    @Composable
    private fun Onboarding(step: OnboardingStep) {
        val noop = OnboardingActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        OnboardingContent(OnboardingState(today, step = step), noop)
    }

    @Test fun todayTrackingLight() = checkTheme(false) { TodayTracking() }

    @Test fun todayTrackingDark() = checkTheme(true) { TodayTracking() }

    @Test fun todayOnPillLight() = checkTheme(false) { TodayOnPill() }

    @Test fun todayOnPillDark() = checkTheme(true) { TodayOnPill() }

    @Test fun calendarLight() = checkTheme(false) { Calendar() }

    @Test fun calendarDark() = checkTheme(true) { Calendar() }

    @Test fun insightsLight() = checkTheme(false) { Insights() }

    @Test fun insightsDark() = checkTheme(true) { Insights() }

    @Test fun settingsLight() = checkTheme(false) { Settings() }

    @Test fun settingsDark() = checkTheme(true) { Settings() }

    @Test fun logLight() = checkTheme(false) { Log() }

    @Test fun logDark() = checkTheme(true) { Log() }

    @Test fun lockScreen() = checkTheme(false) { LockScreen(onUnlock = {}) }

    @Test fun onboardingWelcome() = checkTheme(false) { Onboarding(OnboardingStep.WELCOME) }

    @Test fun onboardingLastPeriod() = checkTheme(false) { Onboarding(OnboardingStep.LAST_PERIOD) }

    @Test fun onboardingCycleLength() = checkTheme(false) { Onboarding(OnboardingStep.CYCLE_LENGTH) }

    @Test fun onboardingGoal() = checkTheme(true) { Onboarding(OnboardingStep.GOAL) }
}
