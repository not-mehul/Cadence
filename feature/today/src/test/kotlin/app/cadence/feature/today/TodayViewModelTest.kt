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
            entries,
            FakeCycleAnnotationRepository(),
            FakeDateSource(today),
            contraception,
            medications,
            tags,
        )
    }
    private val entries = FakeDayEntryRepository(periods)
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
    fun `quick period started logs the period once, then says it's already logged`() =
        runTest {
            ready()
            vm.quickPeriodStarted()
            runCurrent()
            assertEquals(TodayEvent.PeriodLogged(days(20, 23)), vm.events.first())

            vm.quickPeriodStarted()
            runCurrent()
            assertEquals(TodayEvent.NothingToDo(QuickNote.ALREADY_ON_PERIOD), vm.events.first())
            assertEquals(4, periods.flowByDay.value.size)
        }

    @Test
    fun `quick dose done records today's dose, once, and only when one is due`() =
        runTest {
            vm.quickDoseTaken()
            ready()
            vm.quickDoseTaken()
            runCurrent()
            assertEquals(TodayEvent.NothingToDo(QuickNote.NO_DOSE_TODAY), vm.events.first())

            // Pill day 19 of 21.
            val pill = ContraceptiveSchedule.typical(ContraceptiveMethod.PILL, LocalDate.of(2026, 3, 2))
            settings.update { it.copy(goal = Goal.BIRTH_CONTROL, contraception = pill) }
            ready()
            vm.quickDoseTaken()
            runCurrent()
            assertEquals(TodayEvent.DoseRecorded(today, null), vm.events.first())
            assertEquals(DoseStatus.TAKEN, ready().birthControl!!.todayStatus)

            vm.quickDoseTaken()
            runCurrent()
            assertEquals(TodayEvent.NothingToDo(QuickNote.DOSE_ALREADY_TAKEN), vm.events.first())

            // In the break (no placebo pills), nothing is due.
            settings.update { it.copy(contraception = pill.copy(packStart = LocalDate.of(2026, 2, 25))) }
            ready()
            vm.quickDoseTaken()
            runCurrent()
            assertEquals(TodayEvent.NothingToDo(QuickNote.NO_DOSE_TODAY), vm.events.first())
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

    @Test
    fun `a heads-up for what usually comes at this point in the cycle`() =
        runTest {
            // Cramps on the first day of each of four periods, 28 days apart (three completed cycles). The next period
            // is expected on 21 March, the day after today, so cramps are a heads-up for tomorrow.
            listOf(
                LocalDate.of(2025, 11, 29),
                LocalDate.of(2025, 12, 27),
                LocalDate.of(2026, 1, 24),
                LocalDate.of(2026, 2, 21),
            ).forEach { start ->
                periods.logPeriodDays((0L until 4L).map { start.plusDays(it) })
                entries.save(
                    app.cadence.core.domain.log.DayEntry(
                        start,
                        flow = app.cadence.core.domain.model.MenstrualFlow.MEDIUM,
                        symptoms = mapOf("cramps" to app.cadence.core.domain.log.Severity.MILD),
                    ),
                )
            }

            val headsUp = ready().headsUp.single()
            assertEquals("cramps", headsUp.pattern.itemId)
            assertEquals(1, headsUp.inDays)

            settings.settings.value =
                settings.settings.value.copy(
                    goal = app.cadence.core.domain.model.Goal.BIRTH_CONTROL,
                    contraception =
                        app.cadence.core.domain.contraception.ContraceptiveSchedule.typical(
                            app.cadence.core.domain.contraception.ContraceptiveMethod.PILL,
                            LocalDate.of(2026, 3, 2),
                        ),
                )
            runCurrent()
            assertEquals(emptyList(), assertIs<TodayUiState.Ready>(vm.uiState.value).headsUp, "not on a schedule")
        }
}
