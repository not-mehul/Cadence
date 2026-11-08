package app.cadence.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CycleInsights
import app.cadence.core.domain.cycles.CyclePrediction
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.cycles.CycleStats
import app.cadence.core.domain.cycles.HealthFlag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** One completed cycle in the history list and chart. */
data class CycleRow(
    val start: LocalDate,
    val lengthDays: Int,
    val periodDays: Int,
    val excluded: Boolean,
) {
    /** The cycle's last day, the day before the next period started. */
    val end: LocalDate
        get() = start.plusDays(lengthDays - 1L)
}

data class CurrentCycle(
    val start: LocalDate,
    val day: Int,
)

sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    data class Ready(
        val stats: CycleStats,
        val prediction: CyclePrediction?,
        /** Completed cycles, oldest first. */
        val cycles: List<CycleRow>,
        val currentCycle: CurrentCycle?,
        val flags: List<HealthFlag>,
    ) : InsightsUiState {
        /** The chart shows the most recent cycles. */
        val chartCycles: List<CycleRow>
            get() = cycles.takeLast(CHART_CYCLES)

        companion object {
            const val CHART_CYCLES = 12
        }
    }
}

@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        periods: PeriodRepository,
        settings: SettingsRepository,
        entries: DayEntryRepository,
        private val annotations: CycleAnnotationRepository,
        dates: DateSource,
    ) : ViewModel() {
        val uiState: StateFlow<InsightsUiState> =
            combine(
                periods.flowByDay,
                settings.settings,
                annotations.excludedStarts,
                entries.painByDay,
                dates.today,
            ) { flowByDay, s, excluded, pain, today ->
                val history = CycleHistory.from(flowByDay, excluded)
                val periodLengths = history.periods.associate { it.start to it.length }
                val current = history.latestPeriodStartedBy(today)
                InsightsUiState.Ready(
                    stats = CycleInsights.stats(history, today),
                    prediction = CyclePredictor.predict(history, s.cycleDefaults, today),
                    cycles =
                        history.cycles.map {
                            CycleRow(it.start, it.length, periodLengths.getValue(it.start), it.excluded)
                        },
                    currentCycle =
                        current?.let {
                            CurrentCycle(
                                it.start,
                                ChronoUnit.DAYS.between(it.start, today).toInt() + 1,
                            )
                        },
                    flags = CycleInsights.healthFlags(history, pain, s.goal, today),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState.Loading)

        fun setIncluded(
            cycleStart: LocalDate,
            included: Boolean,
        ) {
            viewModelScope.launch { annotations.setExcluded(cycleStart, excluded = !included) }
        }
    }
