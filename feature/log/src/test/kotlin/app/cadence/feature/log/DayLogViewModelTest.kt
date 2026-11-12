package app.cadence.feature.log

import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.testing.FakeContraceptionRepository
import app.cadence.core.testing.FakeCustomTagRepository
import app.cadence.core.testing.FakeDayEntryRepository
import app.cadence.core.testing.FakeMedicationRepository
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DayLogViewModelTest {
    // A standard dispatcher, so the note's save delay runs on virtual time.
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val today = LocalDate.of(2026, 3, 20)
    private val periods = FakePeriodRepository()
    private val entries = FakeDayEntryRepository(periods)
    private val settings =
        FakeSettingsRepository(UserSettings(onboardingComplete = true, hiddenCategories = setOf(LogCategory.SEX)))
    private val contraception = FakeContraceptionRepository()
    private val medications = FakeMedicationRepository()
    private val tags = FakeCustomTagRepository(entries)
    private val vm by lazy { DayLogViewModel(entries, settings, { today }, contraception, medications, tags) }

    private fun kotlinx.coroutines.test.TestScope.ready(date: LocalDate = today): DayLogUiState.Ready {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.load(date)
        runCurrent()
        return assertIs(vm.uiState.value)
    }

    @Test
    fun `loads the saved entry and the hidden categories`() =
        runTest {
            entries.save(DayEntry(today, moods = setOf("calm")))

            val state = ready()

            assertEquals(setOf("calm"), state.entry.moods)
            assertEquals(true, state.isToday)
            assertEquals(false, state.shows(LogCategory.SEX))
            assertEquals(true, state.shows(LogCategory.MOOD))
        }

    @Test
    fun `other days are not today`() =
        runTest {
            assertEquals(false, ready(today.minusDays(3)).isToday)
        }

    @Test
    fun `taps save immediately`() =
        runTest {
            ready()

            vm.edit { it.cycleSymptom("cramps") }
            vm.edit { it.copy(flow = MenstrualFlow.HEAVY) }
            runCurrent()

            assertEquals(mapOf("cramps" to Severity.MILD), entries.entries.value[today]?.symptoms)
            assertEquals(MenstrualFlow.HEAVY, periods.flowByDay.value[today], "flow is a period day for predictions")
        }

    @Test
    fun `notes save after a pause`() =
        runTest {
            ready()

            vm.editNote("Felt")
            vm.editNote("Felt fine")
            advanceTimeBy(100)
            runCurrent()
            assertNull(entries.entries.value[today]?.note, "not saved while typing")

            advanceTimeBy(500)
            runCurrent()
            assertEquals("Felt fine", entries.entries.value[today]?.note)
        }

    @Test
    fun `reopening a day after it changed elsewhere never writes back the old log`() =
        runTest {
            ready()
            vm.close()
            runCurrent()

            // While the sheet is closed, "Period started" logs today (on Today or the calendar).
            periods.logPeriodDays(listOf(today))
            vm.load(today)
            runCurrent()
            assertEquals(MenstrualFlow.MEDIUM, assertIs<DayLogUiState.Ready>(vm.uiState.value).entry.flow)

            vm.edit { it.toggleMood("calm") }
            runCurrent()
            assertEquals(MenstrualFlow.MEDIUM, periods.flowByDay.value[today], "the period day survives")
        }

    @Test
    fun `closing the sheet saves a pending note`() =
        runTest {
            ready()

            vm.editNote("Typed then closed")
            vm.close()
            runCurrent()

            assertEquals("Typed then closed", entries.entries.value[today]?.note)
        }

    @Test
    fun `a blank note is removed and long notes are cut`() =
        runTest {
            ready()

            vm.editNote("   ")
            advanceTimeBy(500)
            runCurrent()
            assertNull(entries.entries.value[today])

            vm.editNote("x".repeat(DayEntry.MAX_NOTE_LENGTH + 10))
            advanceTimeBy(500)
            runCurrent()
            assertEquals(
                DayEntry.MAX_NOTE_LENGTH,
                entries.entries.value[today]
                    ?.note
                    ?.length,
            )
        }

    @Test
    fun `switching days saves the pending note first`() =
        runTest {
            ready()

            vm.editNote("for today")
            vm.load(today.minusDays(1))
            runCurrent()

            assertEquals("for today", entries.entries.value[today]?.note)
            assertEquals(today.minusDays(1), assertIs<DayLogUiState.Ready>(vm.uiState.value).entry.date)
        }

    @Test
    fun `the day's birth control dose is shown and recorded on a schedule`() =
        runTest {
            assertNull(ready().dose)
            settings.update {
                it.copy(
                    goal = Goal.BIRTH_CONTROL,
                    contraception = ContraceptiveSchedule.typical(ContraceptiveMethod.RING, LocalDate.of(2026, 3, 2)),
                )
            }
            // 23 March is when the ring comes out.
            val state = ready(LocalDate.of(2026, 3, 23))
            assertEquals(DoseAction.REMOVE_RING, state.dose)

            vm.recordDose(DoseStatus.TAKEN)
            runCurrent()
            assertEquals(DoseStatus.TAKEN, (vm.uiState.value as DayLogUiState.Ready).doseStatus)
            assertEquals(mapOf(LocalDate.of(2026, 3, 23) to DoseStatus.TAKEN), contraception.doses.value)
        }

    @Test
    fun `active medications, and stopped ones taken that day`() =
        runTest {
            val iron = medications.save(Medication(0, "Iron", MedicationUse.DAILY))
            val old = medications.save(Medication(0, "Old", MedicationUse.DAILY, active = false))
            medications.save(Medication(0, "Unused", MedicationUse.AS_NEEDED, active = false))
            medications.setTaken(today, old, true)

            assertEquals(
                listOf("Iron" to false, "Old" to true),
                ready().medications.map {
                    it.medication.name to
                        it.taken
                },
            )

            vm.setMedicationTaken(iron, true)
            runCurrent()
            assertEquals(mapOf(today to setOf(old, iron)), medications.doses.value)
        }

    @Test
    fun `a new tag is created and added to the day, once`() =
        runTest {
            ready()
            vm.createTag("Travel")
            runCurrent()
            vm.createTag("travel")
            vm.createTag(" ")
            runCurrent()

            val state = ready()
            assertEquals(listOf("Travel"), state.tags.map { it.label })
            assertEquals(setOf(state.tags.single().id), state.entry.tags)
            assertEquals(
                setOf(state.tags.single().id),
                entries.entries.value
                    .getValue(today)
                    .tags,
            )
        }
}
