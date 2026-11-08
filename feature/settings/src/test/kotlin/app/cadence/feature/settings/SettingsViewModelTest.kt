package app.cadence.feature.settings

import app.cadence.core.data.security.AppLock
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.security.SecuritySettings
import app.cadence.core.testing.FakeCustomTagRepository
import app.cadence.core.testing.FakeDataEraser
import app.cadence.core.testing.FakeDateSource
import app.cadence.core.testing.FakeMedicationRepository
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val periods = FakePeriodRepository(mapOf(LocalDate.of(2026, 3, 1) to MenstrualFlow.MEDIUM))
    private val settings = FakeSettingsRepository()
    private val eraser = FakeDataEraser(periods, settings)
    private val appLock = AppLock()
    private val medications = FakeMedicationRepository()
    private val tags = FakeCustomTagRepository()
    private val today = LocalDate.of(2026, 3, 20)
    private val vm by lazy { SettingsViewModel(settings, eraser, appLock, medications, tags, FakeDateSource(today)) }

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

    @Test
    fun `a pill schedule is set from the day of the pack, and removed`() {
        val choice =
            BirthControlChoice(ContraceptiveMethod.PILL, activeDays = 24, breakDays = 4, placebo = true, dayToday = 5)
        vm.setContraception(SettingsViewModel.scheduleFrom(choice, today, previous = null))

        val schedule = settings.settings.value.contraception!!
        assertEquals(LocalDate.of(2026, 3, 16), schedule.packStart)
        assertEquals(5, schedule.dayOfPack(today))
        assertTrue(schedule.placebo)
        assertEquals(today, schedule.since)

        vm.setContraception(null)
        assertEquals(null, settings.settings.value.contraception)
    }

    @Test
    fun `saving an unchanged schedule keeps its history and reminder`() {
        val previous =
            ContraceptiveSchedule.typical(ContraceptiveMethod.RING, LocalDate.of(2026, 2, 2)).copy(
                since = LocalDate.of(2026, 2, 2),
                reminderEnabled = true,
                reminderTime = LocalTime.of(21, 0),
            )
        val same = BirthControlChoice(ContraceptiveMethod.RING, 21, 7, false, previous.dayOfPack(today))
        assertEquals(previous, SettingsViewModel.scheduleFrom(same, today, previous))

        // A different day of the pack is a new layout: missed-dose prompts restart from today.
        val moved = SettingsViewModel.scheduleFrom(same.copy(dayToday = 1), today, previous)!!
        assertEquals(today, moved.since)
        assertEquals(today, moved.packStart)
        assertTrue(moved.reminderEnabled)
        assertEquals(LocalTime.of(21, 0), moved.reminderTime)

        // Placebo only makes sense for pills with a break.
        assertFalse(SettingsViewModel.scheduleFrom(same.copy(placebo = true), today, null)!!.placebo)
        assertEquals(null, SettingsViewModel.scheduleFrom(BirthControlChoice(null), today, previous))
    }

    @Test
    fun `medications and tags are managed from settings`() =
        kotlinx.coroutines.test.runTest {
            vm.saveMedication(Medication(0, "Iron", MedicationUse.DAILY, LocalTime.of(8, 0)))
            vm.createTag("Travel")
            val iron = medications.medications.value.single()
            val travel = tags.tags.value.single()

            vm.renameTag(travel.id, "Trips")
            vm.saveMedication(iron.copy(active = false))
            assertEquals(listOf("Trips"), tags.tags.value.map { it.label })
            assertFalse(
                medications.medications.value
                    .single()
                    .active,
            )

            backgroundScope.launch { vm.tracking.collect {} }
            runCurrent()
            assertEquals(
                TrackingSettingsState(today, medications.medications.value, tags.tags.value),
                vm.tracking.value,
            )

            vm.deleteMedication(iron.id)
            vm.deleteTag(travel.id)
            assertEquals(emptyList(), medications.medications.value)
            assertEquals(emptyList(), tags.tags.value)
        }
}
