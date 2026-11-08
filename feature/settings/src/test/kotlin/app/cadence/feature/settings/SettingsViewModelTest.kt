package app.cadence.feature.settings

import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.testing.FakeDataEraser
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val periods = FakePeriodRepository(mapOf(LocalDate.of(2026, 3, 1) to MenstrualFlow.MEDIUM))
    private val settings = FakeSettingsRepository()
    private val eraser = FakeDataEraser(periods, settings)
    private val vm by lazy { SettingsViewModel(settings, eraser) }

    @Test
    fun `setters update settings`() {
        vm.setCycleLength(31)
        vm.setPeriodLength(6)
        vm.setGoal(Goal.BIRTH_CONTROL)
        vm.setTheme(ThemePreference.DARK)

        assertEquals(
            UserSettings(
                Goal.BIRTH_CONTROL,
                CycleDefaults(31, 6),
                theme = ThemePreference.DARK,
                onboardingComplete = true,
            ),
            settings.settings.value,
        )
    }

    @Test
    fun `delete all erases everything`() {
        vm.deleteAllData()

        assertTrue(eraser.erased)
        assertEquals(emptyMap(), periods.flowByDay.value)
        assertEquals(false, settings.settings.value.onboardingComplete)
    }

    @Test
    fun `length ranges keep the period shorter than the cycle`() {
        assertEquals(15..60, SettingsViewModel.cycleLengthRange(CycleDefaults(28, 5)))
        assertEquals(16..60, SettingsViewModel.cycleLengthRange(CycleDefaults(28, 15)))
        assertEquals(1..15, SettingsViewModel.periodLengthRange(CycleDefaults(28, 5)))
        assertEquals(1..14, SettingsViewModel.periodLengthRange(CycleDefaults(15, 5)))
    }
}
