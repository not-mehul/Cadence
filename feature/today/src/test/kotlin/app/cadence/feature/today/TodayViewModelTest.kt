package app.cadence.feature.today

import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.contraception.PendingDose
import app.cadence.core.domain.cycles.CycleStatus
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.testing.FakeContraceptionRepository
import app.cadence.core.testing.FakeCustomTagRepository
import app.cadence.core.testing.FakeCycleAnnotationRepository
import app.cadence.core.testing.FakeDateSource
import app.cadence.core.testing.FakeDayEntryRepository
import app.cadence.core.testing.FakeMedicationRepository
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
    private val vm by lazy {
        TodayViewModel(
            periods,
            settings,
            FakeDayEntryRepository(periods),
            FakeCycleAnnotationRepository(),
            FakeDateSource(today),
            contraception,
            medications,
            tags,
        )
    }
    private val contraception = FakeContraceptionRepository()
    private val medications = FakeMedicationRepository()
    private val tags = FakeCustomTagRepository()

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

    @Test
    fun `on a pill schedule, today's dose, missed doses and the next one`() =
        runTest {
            // Pack from 2 March: 20 March is pill day 19.
            val pill =
                ContraceptiveSchedule
                    .typical(
                        ContraceptiveMethod.PILL,
                        LocalDate.of(2026, 3, 2),
                    ).copy(since = LocalDate.of(2026, 3, 16))
            settings.update { it.copy(goal = Goal.BIRTH_CONTROL, contraception = pill) }
            contraception.record(LocalDate.of(2026, 3, 16), DoseStatus.TAKEN)
            contraception.record(LocalDate.of(2026, 3, 18), DoseStatus.MISSED)
            contraception.record(LocalDate.of(2026, 3, 19), DoseStatus.TAKEN)

            val bc = ready().birthControl!!
            assertEquals(null, bc.todayStatus)
            assertEquals(listOf(PendingDose(LocalDate.of(2026, 3, 17), DoseAction.ACTIVE_PILL)), bc.missed)
            assertEquals(PendingDose(LocalDate.of(2026, 3, 21), DoseAction.ACTIVE_PILL), bc.next)
            assertEquals(CycleStatus.PackActive(19, 3), ready().snapshot.status)

            vm.recordDose(today, DoseStatus.TAKEN)
            vm.recordDose(LocalDate.of(2026, 3, 17), DoseStatus.TAKEN)
            runCurrent()
            assertEquals(DoseStatus.TAKEN, ready().birthControl!!.todayStatus)
            assertEquals(emptyList(), ready().birthControl!!.missed)

            vm.recordDose(today, null)
            runCurrent()
            assertEquals(null, ready().birthControl!!.todayStatus)
        }

    @Test
    fun `no schedule in tracking mode, even if one is saved`() =
        runTest {
            settings.update { it.copy(contraception = ContraceptiveSchedule.typical(ContraceptiveMethod.RING, today)) }
            assertEquals(null, ready().birthControl)
        }

    @Test
    fun `the next ring change comes from the schedule`() {
        val ring = ContraceptiveSchedule.typical(ContraceptiveMethod.RING, LocalDate.of(2026, 3, 2))
        assertEquals(
            PendingDose(LocalDate.of(2026, 3, 23), DoseAction.REMOVE_RING),
            TodayViewModel.birthControlToday(ring, emptyMap(), today).next,
        )
    }

    @Test
    fun `active medications are ticked off for today`() =
        runTest {
            val iron = medications.save(Medication(0, "Iron", MedicationUse.DAILY))
            medications.save(Medication(0, "Old", MedicationUse.DAILY, active = false))
            tags.create("Travel")

            assertEquals(
                listOf(MedicationToday(Medication(iron, "Iron", MedicationUse.DAILY), false)),
                ready().medications,
            )
            vm.setMedicationTaken(iron, true)
            runCurrent()
            assertEquals(true, ready().medications.single().taken)
            assertEquals(mapOf(1L to "Travel"), ready().tagLabels)
        }
}
