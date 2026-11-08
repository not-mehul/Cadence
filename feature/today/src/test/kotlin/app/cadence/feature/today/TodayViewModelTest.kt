package app.cadence.feature.today

import app.cadence.core.domain.cycles.CycleStatus
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
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
class TodayViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 20)
    private val periods = FakePeriodRepository()
    private val settings =
        FakeSettingsRepository(UserSettings(cycleDefaults = CycleDefaults(28, 4), onboardingComplete = true))
    private val vm by lazy { TodayViewModel(periods, settings, FakeDayEntryRepository(periods), FakeDateSource(today)) }

    private fun days(
        from: Int,
        to: Int,
    ) = (from..to).map { LocalDate.of(2026, 3, it) }

    private fun kotlinx.coroutines.test.TestScope.ready(): TodayUiState.Ready {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return assertIs<TodayUiState.Ready>(vm.uiState.value)
    }

    @Test
    fun `no data until a period is logged`() =
        runTest {
            assertEquals(CycleStatus.NoData, ready().snapshot.status)
        }

    @Test
    fun `starting a period logs the typical length and offers undo`() =
        runTest {
            ready()

            vm.startPeriod()
            runCurrent()

            assertEquals(days(20, 23).associateWith { MenstrualFlow.MEDIUM }, periods.flowByDay.value)
            assertEquals(TodayEvent.PeriodLogged(days(20, 23)), vm.events.first())
            assertEquals(CycleStatus.OnPeriod(1), (vm.uiState.value as TodayUiState.Ready).snapshot.status)
        }

    @Test
    fun `undo removes exactly the logged days`() =
        runTest {
            ready()
            vm.startPeriod()
            runCurrent()

            vm.undoLogged((vm.events.first() as TodayEvent.PeriodLogged).days)
            runCurrent()

            assertEquals(emptyMap(), periods.flowByDay.value)
        }

    @Test
    fun `ending yesterday trims today and later days`() =
        runTest {
            periods.flowByDay.value = days(17, 21).associateWith { MenstrualFlow.MEDIUM }
            ready()

            vm.endPeriod(today.minusDays(1))
            runCurrent()

            assertEquals(days(17, 19).toSet(), periods.flowByDay.value.keys)
        }

    @Test
    fun `ending on a day outside the period does nothing`() =
        runTest {
            periods.flowByDay.value = days(17, 21).associateWith { MenstrualFlow.MEDIUM }
            ready()

            vm.endPeriod(LocalDate.of(2026, 3, 1))
            runCurrent()

            assertEquals(5, periods.flowByDay.value.size)
        }

    @Test
    fun `logging today extends a period that ended yesterday`() =
        runTest {
            periods.flowByDay.value = days(15, 19).associateWith { MenstrualFlow.MEDIUM }
            assertEquals(true, ready().snapshot.canExtendPeriod)

            vm.logToday()
            runCurrent()

            assertEquals(CycleStatus.OnPeriod(6), (vm.uiState.value as TodayUiState.Ready).snapshot.status)
        }
}
