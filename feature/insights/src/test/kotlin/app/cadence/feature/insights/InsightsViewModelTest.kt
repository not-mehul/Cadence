package app.cadence.feature.insights

import app.cadence.core.domain.cycles.HealthFlag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.testing.FakeCycleAnnotationRepository
import app.cadence.core.testing.FakeDateSource
import app.cadence.core.testing.FakeDayEntryRepository
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 4, 10)
    private val periods = FakePeriodRepository()
    private val entries = FakeDayEntryRepository(periods)
    private val annotations = FakeCycleAnnotationRepository()
    private val vm by lazy {
        InsightsViewModel(periods, FakeSettingsRepository(), entries, annotations, FakeDateSource(today))
    }

    private fun period(
        start: LocalDate,
        days: Int = 5,
    ) = (0L until days).associate { start.plusDays(it) to MenstrualFlow.MEDIUM }

    private fun TestScope.ready(): InsightsUiState.Ready {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return assertIs(vm.uiState.value)
    }

    @Test
    fun `nothing logged`() =
        runTest {
            val s = ready()
            assertEquals(emptyList(), s.cycles)
            assertNull(s.currentCycle)
            assertNull(s.prediction)
        }

    @Test
    fun `cycles, current cycle and stats`() =
        runTest {
            periods.flowByDay.value = period(LocalDate.of(2026, 1, 1)) + period(LocalDate.of(2026, 1, 29), 4) +
                period(LocalDate.of(2026, 2, 28)) + period(LocalDate.of(2026, 3, 28))

            val s = ready()

            assertEquals(listOf(28, 30, 28), s.cycles.map { it.lengthDays })
            assertEquals(listOf(5, 4, 5), s.cycles.map { it.periodDays })
            assertEquals(LocalDate.of(2026, 2, 27), s.cycles[1].end)
            assertEquals(CurrentCycle(LocalDate.of(2026, 3, 28), day = 14), s.currentCycle)
            assertEquals(3, s.stats.cyclesCounted)
        }

    @Test
    fun `excluding a cycle updates stats, and including it again restores them`() =
        runTest {
            periods.flowByDay.value = period(LocalDate.of(2026, 1, 1)) + period(LocalDate.of(2026, 1, 29)) +
                period(LocalDate.of(2026, 3, 20))
            ready()

            vm.setIncluded(LocalDate.of(2026, 1, 29), included = false)
            runCurrent()
            val excluded = assertIs<InsightsUiState.Ready>(vm.uiState.value)
            assertEquals(listOf(false, true), excluded.cycles.map { it.excluded })
            assertEquals(1, excluded.stats.cyclesCounted)

            vm.setIncluded(LocalDate.of(2026, 1, 29), included = true)
            runCurrent()
            assertEquals(2, assertIs<InsightsUiState.Ready>(vm.uiState.value).stats.cyclesCounted)
        }

    @Test
    fun `logged pain feeds the pain flag`() =
        runTest {
            val starts = listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 29), LocalDate.of(2026, 2, 26))
            periods.flowByDay.value = starts.fold(emptyMap()) { acc, s -> acc + period(s) }
            starts.forEach { s ->
                entries.save(DayEntry(s, flow = MenstrualFlow.MEDIUM, painLevel = 8))
                entries.save(DayEntry(s.plusDays(1), flow = MenstrualFlow.MEDIUM, painLevel = 7))
            }

            assertEquals(listOf<HealthFlag>(HealthFlag.SeverePain(3)), ready().flags)
        }
}
