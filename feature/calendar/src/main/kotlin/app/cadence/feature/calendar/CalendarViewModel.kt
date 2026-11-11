package app.cadence.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.CustomTagRepository
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.cycles.CalendarDays
import app.cadence.core.domain.cycles.CalendarMarks
import app.cadence.core.domain.cycles.CycleDayInfo
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CyclePrediction
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.cycles.DayMark
import app.cadence.core.domain.cycles.PeriodEditor
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.MenstrualFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

sealed interface CalendarUiState {
    data object Loading : CalendarUiState

    data class Ready(
        val today: LocalDate,
        val flowByDay: Map<LocalDate, MenstrualFlow>,
        val prediction: CyclePrediction?,
        /** Days with something logged besides flow (shown as a dot). */
        val detailDates: Set<LocalDate> = emptySet(),
        /** Set when fertile-window estimates apply (tracking mode); null on hormonal birth control. */
        val lutealPhaseLength: Int? = null,
        /** On a pill, patch or ring schedule, its breaks are the expected bleeds. */
        val schedule: ContraceptiveSchedule? = null,
        /** The day whose details are shown under the month. */
        val selected: LocalDate = today,
        /** What's logged on [selected]; null until it has loaded. */
        val selectedEntry: DayEntry? = null,
        val tagLabels: Map<Long, String> = emptyMap(),
        /** How many days "Period started" fills in: the expected period length. */
        val expectedPeriodLength: Int = DEFAULT_PERIOD_LENGTH,
    ) : CalendarUiState {
        private val history: CycleHistory by lazy { CycleHistory.from(flowByDay) }

        val showsFertileWindow: Boolean
            get() = lutealPhaseLength != null

        fun marksFor(month: YearMonth): Map<LocalDate, DayMark> =
            schedule?.let { CalendarMarks.forSchedule(month, flowByDay, it, today) }
                ?: CalendarMarks.forMonth(month, flowByDay, prediction, lutealPhaseLength)

        fun markOn(date: LocalDate): DayMark = marksFor(YearMonth.from(date))[date] ?: DayMark.NONE

        /** Past days and today can be toggled; future days can only be cleared (e.g. a pre-filled period). */
        fun canToggle(date: LocalDate): Boolean = !date.isAfter(today) || isLogged(date)

        /** Only days up to today can be logged. */
        fun canOpenLog(date: LocalDate): Boolean = !date.isAfter(today)

        fun isLogged(date: LocalDate): Boolean = flowByDay[date]?.countsAsPeriod == true

        /** Day N of the cycle, or null before the first period and on a pill, patch or ring schedule. */
        fun cycleDay(date: LocalDate): CycleDayInfo? =
            if (schedule != null) null else CalendarDays.cycleDay(date, today, history, prediction)

        /** Day of the pack on a schedule; null otherwise and before the schedule was set up. */
        fun packDay(date: LocalDate): Int? = schedule?.takeIf { !date.isBefore(it.since) }?.dayOfPack(date)

        /** "Period started" on a day that isn't a period day yet, up to today. */
        fun canStartPeriodOn(date: LocalDate): Boolean = canOpenLog(date) && !isLogged(date)

        /** "Period ended" on a period day with more period days logged after it. */
        fun canEndPeriodOn(date: LocalDate): Boolean =
            isLogged(date) && CalendarDays.periodContaining(history, date)?.end?.isAfter(date) == true

        internal fun daysToClearForEndOn(date: LocalDate): List<LocalDate> {
            val period = CalendarDays.periodContaining(history, date) ?: return emptyList()
            return PeriodEditor.daysToClearForEnd(period, date).filter(::isLogged)
        }
    }

    companion object {
        const val DEFAULT_PERIOD_LENGTH = 5
    }
}

sealed interface CalendarEvent {
    /** "Period started" logged these days; offered for undo. */
    data class PeriodLogged(
        val days: List<LocalDate>,
    ) : CalendarEvent
}

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val periods: PeriodRepository,
        settings: SettingsRepository,
        private val entries: DayEntryRepository,
        annotations: CycleAnnotationRepository,
        tags: CustomTagRepository,
        dates: DateSource,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        /** The chosen day as an epoch day, kept across process death; null means today. */
        private val selectedEpochDay: StateFlow<Long?> = savedState.getStateFlow(KEY_SELECTED, null)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val selection: Flow<Pair<LocalDate, DayEntry>> =
            combine(dates.today, selectedEpochDay) { today, epochDay -> epochDay?.let(LocalDate::ofEpochDay) ?: today }
                .flatMapLatest { date -> entries.observe(date).map { date to it } }

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<CalendarUiState> =
            dates.today
                .flatMapLatest { today ->
                    val details = entries.observeDatesWithDetails(today.minusYears(DETAIL_YEARS), today)
                    val base =
                        combine(
                            periods.flowByDay,
                            settings.settings,
                            details,
                            annotations.excludedStarts,
                        ) { flowByDay, s, detailDates, excluded ->
                            val schedule = s.activeSchedule
                            val prediction =
                                if (schedule != null) {
                                    null
                                } else {
                                    CyclePredictor.predict(
                                        CycleHistory.from(flowByDay, excluded),
                                        s.cycleDefaults,
                                        today,
                                    )
                                }
                            val luteal = s.lutealPhaseLength.takeIf { s.goal.showsOvulationEstimates }
                            CalendarUiState.Ready(
                                today = today,
                                flowByDay = flowByDay,
                                prediction = prediction,
                                detailDates = detailDates,
                                lutealPhaseLength = luteal,
                                schedule = schedule,
                                expectedPeriodLength = prediction?.periodLength ?: s.cycleDefaults.periodLength,
                            )
                        }
                    combine(base, selection, tags.tags) { ready, (date, entry), tagList ->
                        ready.copy(
                            selected = date,
                            selectedEntry = entry,
                            tagLabels = tagList.associate { it.id to it.label },
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState.Loading)

        private val eventChannel = Channel<CalendarEvent>(Channel.BUFFERED)
        val events: Flow<CalendarEvent> = eventChannel.receiveAsFlow()

        fun select(date: LocalDate) {
            savedState[KEY_SELECTED] = date.toEpochDay()
        }

        fun toggle(date: LocalDate) =
            withReady { state ->
                if (!state.canToggle(date)) return@withReady
                if (state.isLogged(date)) periods.clearPeriodDays(listOf(date)) else periods.logPeriodDays(listOf(date))
            }

        /**
         * A period started on [date], which can be long past: logs the expected number of days from
         * there, as "Period started" on Today does, and predictions follow from the new history.
         */
        fun startPeriodOn(date: LocalDate) =
            withReady { state ->
                if (!state.canStartPeriodOn(date)) return@withReady
                val added = periods.logPeriodDays(PeriodEditor.daysForStart(date, state.expectedPeriodLength))
                if (added.isNotEmpty()) eventChannel.send(CalendarEvent.PeriodLogged(added))
            }

        /** The period [date] is in ended that day: clears the days logged after it. */
        fun endPeriodOn(date: LocalDate) =
            withReady { state ->
                if (state.canEndPeriodOn(date)) periods.clearPeriodDays(state.daysToClearForEndOn(date))
            }

        fun removePeriodDay(date: LocalDate) =
            withReady { state ->
                if (state.isLogged(date)) periods.clearPeriodDays(listOf(date))
            }

        fun undoLogged(days: List<LocalDate>) {
            viewModelScope.launch { periods.clearPeriodDays(days) }
        }

        private fun withReady(block: suspend (CalendarUiState.Ready) -> Unit) {
            val state = uiState.value as? CalendarUiState.Ready ?: return
            viewModelScope.launch { block(state) }
        }

        private companion object {
            /** How far back day-log dots are loaded. */
            const val DETAIL_YEARS = 5L
            const val KEY_SELECTED = "selected_day"
        }
    }
