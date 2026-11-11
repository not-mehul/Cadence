package app.cadence.feature.calendar

import androidx.lifecycle.SavedStateHandle
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.testing.FakeCustomTagRepository
import app.cadence.core.testing.FakeCycleAnnotationRepository
import app.cadence.core.testing.FakeDateSource
import app.cadence.core.testing.FakeDayEntryRepository
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 20)
    private val periods = FakePeriodRepository()
    private val entries = FakeDayEntryRepository(periods)
    private val settings = FakeSettingsRepository()
    private val tags = FakeCustomTagRepository(entries)
    private val vm by lazy {
        CalendarViewModel(
            periods,
            settings,
            entries,
            FakeCycleAnnotationRepository(),
            tags,
            FakeDateSource(today),
            SavedStateHandle(),
        )
    }

    private fun kotlinx.coroutines.test.TestScope.start() {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    private fun kotlinx.coroutines.test.TestScope.toggle(date: LocalDate) {
        vm.toggle(date)
        runCurrent()
    }

    @Test
    fun `tapping a past day logs it, tapping again clears it`() =
        runTest {
            start()
            val day = LocalDate.of(2026, 3, 2)

            toggle(day)
            assertEquals(mapOf(day to MenstrualFlow.MEDIUM), periods.flowByDay.value)

            toggle(day)
            assertEquals(emptyMap(), periods.flowByDay.value)
        }

    @Test
    fun `today can be logged`() =
        runTest {
            start()
            toggle(today)
            assertEquals(setOf(today), periods.flowByDay.value.keys)
        }

    @Test
    fun `future days cannot be logged`() =
        runTest {
            start()
            toggle(today.plusDays(1))
            assertEquals(emptyMap(), periods.flowByDay.value)
        }

    @Test
    fun `a pre-filled future day can be cleared`() =
        runTest {
            val tomorrow = today.plusDays(1)
            periods.flowByDay.value = mapOf(today to MenstrualFlow.MEDIUM, tomorrow to MenstrualFlow.MEDIUM)
            start()

            toggle(tomorrow)

            assertEquals(setOf(today), periods.flowByDay.value.keys)
        }

    @Test
    fun `state includes a prediction once a period is logged`() =
        runTest {
            periods.flowByDay.value = mapOf(LocalDate.of(2026, 3, 1) to MenstrualFlow.MEDIUM)
            start()

            val state = assertIs<CalendarUiState.Ready>(vm.uiState.value)
            assertEquals(LocalDate.of(2026, 3, 29), state.prediction?.nextStart)
        }

    @Test
    fun `days with details are marked, flow-only days are not`() =
        runTest {
            entries.save(
                app.cadence.core.domain.log
                    .DayEntry(LocalDate.of(2026, 3, 3), moods = setOf("calm")),
            )
            entries.save(
                app.cadence.core.domain.log
                    .DayEntry(LocalDate.of(2026, 3, 4), flow = MenstrualFlow.LIGHT),
            )
            start()

            val state = assertIs<CalendarUiState.Ready>(vm.uiState.value)
            assertEquals(setOf(LocalDate.of(2026, 3, 3)), state.detailDates)
            assertEquals(false, state.canOpenLog(today.plusDays(1)))
        }

    @Test
    fun `fertile window shows when tracking, not on birth control`() =
        runTest {
            periods.flowByDay.value = mapOf(LocalDate.of(2026, 3, 1) to MenstrualFlow.MEDIUM)
            start()
            assertEquals(14, assertIs<CalendarUiState.Ready>(vm.uiState.value).lutealPhaseLength)

            settings.settings.value =
                settings.settings.value.copy(goal = app.cadence.core.domain.model.Goal.BIRTH_CONTROL)
            runCurrent()
            assertEquals(false, assertIs<CalendarUiState.Ready>(vm.uiState.value).showsFertileWindow)
        }

    private fun ready() = assertIs<CalendarUiState.Ready>(vm.uiState.value)

    private fun days(
        first: LocalDate,
        count: Int,
    ) = (0L until count).map { first.plusDays(it) }.toSet()

    @Test
    fun `today is selected until another day is chosen, and its log comes with it`() =
        runTest {
            val other = LocalDate.of(2026, 2, 14)
            entries.save(DayEntry(other, moods = setOf("happy")))
            val travel = tags.create("Travel")
            start()
            assertEquals(today, ready().selected)
            assertEquals(DayEntry(today), ready().selectedEntry)

            vm.select(other)
            runCurrent()

            assertEquals(other, ready().selected)
            assertEquals(setOf("happy"), ready().selectedEntry?.moods)
            assertEquals(mapOf(travel to "Travel"), ready().tagLabels)
        }

    @Test
    fun `a period started long ago fills in the expected length, and predictions follow`() =
        runTest {
            settings.settings.value = settings.settings.value.copy(cycleDefaults = CycleDefaults(28, 4))
            start()
            val start = LocalDate.of(2026, 1, 10)

            vm.startPeriodOn(start)
            runCurrent()

            assertEquals(days(start, 4), periods.flowByDay.value.keys)
            assertEquals(CalendarEvent.PeriodLogged(days(start, 4).sorted()), vm.events.first())
            // One period: the next is expected a cycle later, and late by now.
            assertEquals(LocalDate.of(2026, 2, 7), ready().prediction?.expectedNextStart)
        }

    @Test
    fun `the expected length comes from logged periods once there are some`() =
        runTest {
            val logged =
                listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 29))
                    .flatMap { days(it, 6) }
                    .associateWith { MenstrualFlow.MEDIUM }
            periods.flowByDay.value = logged
            start()
            assertEquals(6, ready().expectedPeriodLength)

            vm.startPeriodOn(LocalDate.of(2026, 2, 26))
            runCurrent()

            assertEquals(logged.keys + days(LocalDate.of(2026, 2, 26), 6), periods.flowByDay.value.keys)
        }

    @Test
    fun `starting a period on a period day or a future day does nothing`() =
        runTest {
            periods.flowByDay.value = mapOf(LocalDate.of(2026, 3, 2) to MenstrualFlow.HEAVY)
            start()

            vm.startPeriodOn(LocalDate.of(2026, 3, 2))
            vm.startPeriodOn(today.plusDays(2))
            runCurrent()

            assertEquals(mapOf(LocalDate.of(2026, 3, 2) to MenstrualFlow.HEAVY), periods.flowByDay.value)
        }

    @Test
    fun `undo removes exactly the days that were added`() =
        runTest {
            periods.flowByDay.value = mapOf(LocalDate.of(2026, 3, 3) to MenstrualFlow.HEAVY)
            start()

            // A start two days before a logged day joins it; only the new days are offered for undo.
            vm.startPeriodOn(LocalDate.of(2026, 3, 1))
            runCurrent()
            val event = assertIs<CalendarEvent.PeriodLogged>(vm.events.first())
            assertEquals(false, LocalDate.of(2026, 3, 3) in event.days)

            vm.undoLogged(event.days)
            runCurrent()
            assertEquals(mapOf(LocalDate.of(2026, 3, 3) to MenstrualFlow.HEAVY), periods.flowByDay.value)
        }

    @Test
    fun `a period can end on a chosen day, and a single day can be removed`() =
        runTest {
            val start = LocalDate.of(2026, 3, 1)
            periods.flowByDay.value = days(start, 6).associateWith { MenstrualFlow.MEDIUM }
            start()
            assertEquals(true, ready().canEndPeriodOn(LocalDate.of(2026, 3, 3)))
            assertEquals(false, ready().canEndPeriodOn(LocalDate.of(2026, 3, 6)))

            vm.endPeriodOn(LocalDate.of(2026, 3, 3))
            runCurrent()
            assertEquals(days(start, 3), periods.flowByDay.value.keys)

            vm.removePeriodDay(LocalDate.of(2026, 3, 1))
            vm.removePeriodDay(LocalDate.of(2026, 3, 10))
            vm.endPeriodOn(LocalDate.of(2026, 3, 10))
            runCurrent()
            assertEquals(days(LocalDate.of(2026, 3, 2), 2), periods.flowByDay.value.keys)
        }

    @Test
    fun `cycle and pack days for the details card`() =
        runTest {
            periods.flowByDay.value = days(LocalDate.of(2026, 3, 1), 5).associateWith { MenstrualFlow.MEDIUM }
            start()
            assertEquals(20, ready().cycleDay(today)?.day)
            assertEquals(null, ready().packDay(today))

            val pill =
                app.cadence.core.domain.contraception.ContraceptiveSchedule.typical(
                    app.cadence.core.domain.contraception.ContraceptiveMethod.PILL,
                    LocalDate.of(2026, 3, 2),
                )
            settings.settings.value =
                settings.settings.value.copy(
                    goal = app.cadence.core.domain.model.Goal.BIRTH_CONTROL,
                    contraception = pill,
                )
            runCurrent()
            assertEquals(null, ready().cycleDay(today))
            assertEquals(19, ready().packDay(today))
            assertEquals(null, ready().packDay(LocalDate.of(2026, 3, 1)))
        }
}
