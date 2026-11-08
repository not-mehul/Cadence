package app.cadence.feature.settings

import app.cadence.core.data.security.AppLock
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.security.SecuritySettings
import app.cadence.core.testing.FakeDataEraser
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val periods = FakePeriodRepository(mapOf(LocalDate.of(2026, 3, 1) to MenstrualFlow.MEDIUM))
    private val settings = FakeSettingsRepository()
    private val eraser = FakeDataEraser(periods, settings)
    private val appLock = AppLock()
    private val vm by lazy { SettingsViewModel(settings, eraser, appLock) }

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
    fun `flow can never be hidden`() {
        vm.setHiddenCategories(setOf(LogCategory.FLOW, LogCategory.MOOD))
        assertEquals(setOf(LogCategory.MOOD), settings.settings.value.hiddenCategories)
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

    @Test
    fun `enabling the lock unlocks the current session and saves the setting`() {
        assertTrue(appLock.locked.value)
        vm.setLockEnabled(true)

        assertFalse(appLock.locked.value)
        assertTrue(settings.settings.value.security.lockEnabled)
    }

    @Test
    fun `security setters update settings`() {
        vm.setLockEnabled(true)
        vm.setLockTimeout(5)
        vm.setBlockScreenshots(false)
        assertEquals(
            SecuritySettings(lockEnabled = true, lockTimeoutMinutes = 5, blockScreenshots = false),
            settings.settings.value.security,
        )

        vm.setLockEnabled(false)
        assertFalse(settings.settings.value.security.lockEnabled)
        assertEquals(5, settings.settings.value.security.lockTimeoutMinutes)
    }
}
