package app.cadence.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.ContraceptionRepository
import app.cadence.core.data.repository.CustomTagRepository
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.MedicationRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.contraception.MissedDoses
import app.cadence.core.domain.contraception.PendingDose
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.cycles.PeriodEditor
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.patterns.HeadsUp
import app.cadence.core.domain.patterns.SymptomForecast
import app.cadence.core.domain.patterns.SymptomPatterns
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Ready(
        val snapshot: CycleSnapshot,
        val goal: Goal,
        /** Used for "Period started" before there is any prediction. */
        val typicalPeriodLength: Int,
        /** Everything logged today, for the summary card. */
        val todayEntry: DayEntry = DayEntry(snapshot.today),
        /** Birth control (Phase 6): what's due, and what was recorded. */
        val birthControl: BirthControlToday? = null,
        /** Active medications and whether each was taken today. */
        val medications: List<MedicationToday> = emptyList(),
        /** Custom tag labels by id, for the summary card. */
        val tagLabels: Map<Long, String> = emptyMap(),
        /** What the user tends to log around now or tomorrow, from their own patterns. */
        val headsUp: List<HeadsUp> = emptyList(),
    ) : TodayUiState
}

/** The birth control part of Today, on a pill, patch or ring schedule. */
data class BirthControlToday(
    val schedule: ContraceptiveSchedule,
    /** What was recorded for today's dose, if anything. */
    val todayStatus: DoseStatus?,
    /** Required doses in the last week with nothing recorded. */
    val missed: List<PendingDose>,
    /** The next dose after today. */
    val next: PendingDose?,
)

data class MedicationToday(
    val medication: Medication,
    val taken: Boolean,
)

sealed interface TodayEvent {
    /** [days] were newly logged and can be undone. */
    data class PeriodLogged(
        val days: List<LocalDate>,
    ) : TodayEvent

    /** A quick action marked [date]'s dose taken; undo puts back [previous]. */
    data class DoseRecorded(
        val date: LocalDate,
        val previous: DoseStatus?,
    ) : TodayEvent

    /** A quick action that had nothing to do, and why. */
    data class NothingToDo(
        val reason: QuickNote,
    ) : TodayEvent
}

enum class QuickNote {
    ALREADY_ON_PERIOD,
    NO_DOSE_TODAY,
    DOSE_ALREADY_TAKEN,
}

@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val periods: PeriodRepository,
        settings: SettingsRepository,
        entries: DayEntryRepository,
        annotations: CycleAnnotationRepository,
        dates: DateSource,
        private val contraception: ContraceptionRepository,
        private val medications: MedicationRepository,
        tags: CustomTagRepository,
    ) : ViewModel() {
        /** Phase 6 data, bundled so it fits in one combine. */
        private val tracking =
            combine(
                contraception.doses,
                medications.medications,
                medications.doses,
                tags.tags,
                entries.patternItemsByDay,
            ) { doses, meds, medDoses, t, items ->
                Tracking(doses, meds, medDoses, t.associate { it.id to it.label }, items)
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<TodayUiState> =
            dates.today
                .flatMapLatest { today ->
                    combine(
                        periods.flowByDay,
                        settings.settings,
                        entries.observe(today),
                        annotations.excludedStarts,
                        tracking,
                    ) { flowByDay, s, entry, excluded, t ->
                        val snapshot = CycleSnapshot.compute(flowByDay, s, today, excluded)
                        TodayUiState.Ready(
                            snapshot = snapshot,
                            goal = s.goal,
                            typicalPeriodLength = s.cycleDefaults.periodLength,
                            todayEntry = entry,
                            birthControl = s.activeSchedule?.let { birthControlToday(it, t.doses, today) },
                            medications =
                                t.medications
                                    .filter { it.active }
                                    .map { MedicationToday(it, it.id in t.medicationDoses[today].orEmpty()) },
                            tagLabels = t.tagLabels,
                            headsUp =
                                if (s.activeSchedule != null) {
                                    emptyList()
                                } else {
                                    val history = CycleHistory.from(flowByDay, excluded)
                                    SymptomForecast.upcoming(
                                        SymptomPatterns.find(history, t.patternItems),
                                        history,
                                        snapshot.prediction,
                                        today,
                                    )
                                },
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayUiState.Loading)

        private val eventChannel = Channel<TodayEvent>(Channel.BUFFERED)
        val events: Flow<TodayEvent> = eventChannel.receiveAsFlow()

        /** Logs today plus the expected remaining days (docs/PLAN.md §6). */
        fun startPeriod() =
            withReady { state ->
                val length = state.snapshot.prediction?.periodLength ?: state.typicalPeriodLength
                val added = periods.logPeriodDays(PeriodEditor.daysForStart(state.snapshot.today, length))
                if (added.isNotEmpty()) eventChannel.send(TodayEvent.PeriodLogged(added))
            }

        /** "Period started" from a shortcut: as [startPeriod], but says so if today is already logged. */
        fun quickPeriodStarted() =
            withReady { state ->
                if (state.snapshot.currentPeriod?.contains(state.snapshot.today) == true) {
                    eventChannel.send(TodayEvent.NothingToDo(QuickNote.ALREADY_ON_PERIOD))
                } else {
                    startPeriod()
                }
            }

        /** Today's pill, patch or ring done, from a shortcut; offers undo. */
        fun quickDoseTaken() =
            withReady { state ->
                val today = state.snapshot.today
                val bc = state.birthControl
                val note =
                    when {
                        bc == null || state.snapshot.pack?.action == null -> QuickNote.NO_DOSE_TODAY
                        bc.todayStatus == DoseStatus.TAKEN -> QuickNote.DOSE_ALREADY_TAKEN
                        else -> null
                    }
                if (note != null) {
                    eventChannel.send(TodayEvent.NothingToDo(note))
                } else {
                    contraception.record(today, DoseStatus.TAKEN)
                    eventChannel.send(TodayEvent.DoseRecorded(today, bc?.todayStatus))
                }
            }

        fun undoLogged(days: List<LocalDate>) {
            viewModelScope.launch { periods.clearPeriodDays(days) }
        }

        /** The period's last day was [lastDay]; trims any days logged after it. */
        fun endPeriod(lastDay: LocalDate) =
            withReady { state ->
                val period = state.snapshot.currentPeriod ?: return@withReady
                if (lastDay in period) periods.clearPeriodDays(PeriodEditor.daysToClearForEnd(period, lastDay))
            }

        /** Extends a period that seemed over but isn't. */
        fun logToday() =
            withReady { state ->
                periods.logPeriodDays(listOf(state.snapshot.today))
            }

        /** Records (or with null, clears) the birth control dose for [date]. */
        fun recordDose(
            date: LocalDate,
            status: DoseStatus?,
        ) {
            viewModelScope.launch { contraception.record(date, status) }
        }

        fun setMedicationTaken(
            id: Long,
            taken: Boolean,
        ) = withReady { state -> medications.setTaken(state.snapshot.today, id, taken) }

        private fun withReady(block: suspend (TodayUiState.Ready) -> Unit) {
            val state = uiState.value as? TodayUiState.Ready ?: return
            viewModelScope.launch { block(state) }
        }

        private class Tracking(
            val doses: Map<LocalDate, DoseStatus>,
            val medications: List<Medication>,
            val medicationDoses: Map<LocalDate, Set<Long>>,
            val tagLabels: Map<Long, String>,
            val patternItems: Map<LocalDate, Set<String>>,
        )

        companion object {
            internal const val STOP_TIMEOUT_MS = 5_000L

            fun birthControlToday(
                schedule: ContraceptiveSchedule,
                doses: Map<LocalDate, DoseStatus>,
                today: LocalDate,
            ): BirthControlToday {
                // Every pack starts with something to do, so one pack ahead always finds the next dose.
                val next =
                    (1L..schedule.packLength).map(today::plusDays).firstNotNullOfOrNull { date ->
                        schedule.actionOn(date)?.let { PendingDose(date, it) }
                    }
                return BirthControlToday(schedule, doses[today], MissedDoses.find(schedule, doses, today), next)
            }
        }
    }
