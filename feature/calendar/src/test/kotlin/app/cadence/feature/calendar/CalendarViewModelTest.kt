package app.cadence.feature.calendar

import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.testing.FakeDateSource
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
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
    private val vm by lazy { CalendarViewModel(periods, FakeSettingsRepository(), FakeDateSource(today)) }

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
}
