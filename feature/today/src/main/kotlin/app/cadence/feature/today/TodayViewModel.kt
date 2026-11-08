package app.cadence.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.cycles.PeriodEditor
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.Goal
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
    ) : TodayUiState
}

sealed interface TodayEvent {
    /** [days] were newly logged and can be undone. */
    data class PeriodLogged(
        val days: List<LocalDate>,
    ) : TodayEvent
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
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<TodayUiState> =
            dates.today
                .flatMapLatest { today ->
                    combine(
                        periods.flowByDay,
                        settings.settings,
                        entries.observe(today),
                        annotations.excludedStarts,
                    ) { flowByDay, s, entry, excluded ->
                        TodayUiState.Ready(
                            snapshot = CycleSnapshot.compute(flowByDay, s, today, excluded),
                            goal = s.goal,
                            typicalPeriodLength = s.cycleDefaults.periodLength,
                            todayEntry = entry,
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

        private fun withReady(block: suspend (TodayUiState.Ready) -> Unit) {
            val state = uiState.value as? TodayUiState.Ready ?: return
            viewModelScope.launch { block(state) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
