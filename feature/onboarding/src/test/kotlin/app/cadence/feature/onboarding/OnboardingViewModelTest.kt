package app.cadence.feature.onboarding

import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 20)
    private val periods = FakePeriodRepository()
    private val settings = FakeSettingsRepository(UserSettings())
    private val vm by lazy { OnboardingViewModel(periods, settings) { today } }

    private val step get() = vm.state.value.step

    @Test
    fun `steps go forward and back within bounds`() {
        vm.back()
        assertEquals(OnboardingStep.WELCOME, step)
        repeat(10) { vm.next() }
        assertEquals(OnboardingStep.GOAL, step)
        vm.back()
        assertEquals(OnboardingStep.PERIOD_LENGTH, step)
    }

    @Test
    fun `last period must be in the past year`() {
        vm.setLastPeriodStart(today.plusDays(1))
        assertNull(vm.state.value.lastPeriodStart)
        vm.setLastPeriodStart(today.minusYears(1).minusDays(1))
        assertNull(vm.state.value.lastPeriodStart)
        vm.setLastPeriodStart(today)
        assertEquals(today, vm.state.value.lastPeriodStart)
    }

    @Test
    fun `skipping the last period clears it and moves on`() {
        vm.next()
        vm.setLastPeriodStart(today)
        vm.skipLastPeriod()
        assertNull(vm.state.value.lastPeriodStart)
        assertEquals(OnboardingStep.CYCLE_LENGTH, step)
    }

    @Test
    fun `lengths stay valid relative to each other`() {
        vm.setCycleLength(100)
        assertEquals(60, vm.state.value.cycleLength)
        vm.setCycleLength(16)
        vm.setPeriodLength(15)
        assertEquals(15, vm.state.value.periodLength)
        vm.setCycleLength(15)
        assertEquals(16, vm.state.value.cycleLength, "cycle must stay longer than the period")
    }

    @Test
    fun `not sure uses typical values`() {
        vm.setCycleLength(35)
        vm.useTypicalCycleLength()
        assertEquals(28, vm.state.value.cycleLength)
        vm.setPeriodLength(8)
        vm.useTypicalPeriodLength()
        assertEquals(5, vm.state.value.periodLength)
    }

    @Test
    fun `finishing saves settings and logs the last period`() {
        val start = LocalDate.of(2026, 3, 10)
        vm.setLastPeriodStart(start)
        vm.setCycleLength(30)
        vm.setPeriodLength(4)
        vm.setGoal(Goal.BIRTH_CONTROL)

        vm.finish()

        assertEquals(
            UserSettings(goal = Goal.BIRTH_CONTROL, cycleDefaults = CycleDefaults(30, 4), onboardingComplete = true),
            settings.settings.value,
        )
        assertEquals((10..13).associate { LocalDate.of(2026, 3, it) to MenstrualFlow.MEDIUM }, periods.flowByDay.value)
        assertTrue(vm.state.value.finishing)
    }

    @Test
    fun `finishing without a last period logs nothing`() {
        vm.finish()
        assertEquals(emptyMap(), periods.flowByDay.value)
        assertTrue(settings.settings.value.onboardingComplete)
    }

    @Test
    fun `finish runs once`() {
        vm.setLastPeriodStart(today)
        vm.finish()
        periods.flowByDay.value = emptyMap()
        vm.finish()
        assertEquals(emptyMap(), periods.flowByDay.value)
    }
}
