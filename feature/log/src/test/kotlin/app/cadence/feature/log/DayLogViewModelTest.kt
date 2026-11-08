package app.cadence.feature.log

import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.testing.FakeDayEntryRepository
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
    private val vm by lazy { DayLogViewModel(entries, settings) { today } }

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
    fun `closing the sheet saves a pending note`() =
        runTest {
            ready()

            vm.editNote("Typed then closed")
            vm.flush()
            runCurrent()

            assertEquals("Typed then closed", entries.entries.value[today]?.note)
        }

    @Test
    fun `a blank note is removed and long notes are cut`() =
        runTest {
            ready()

            vm.editNote("   ")
            vm.flush()
            runCurrent()
            assertNull(entries.entries.value[today])

            vm.editNote("x".repeat(DayEntry.MAX_NOTE_LENGTH + 10))
            vm.flush()
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
}
