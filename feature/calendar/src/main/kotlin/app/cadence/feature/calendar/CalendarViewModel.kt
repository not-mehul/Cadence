package app.cadence.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.cycles.CalendarMarks
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CyclePrediction
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.cycles.DayMark
import app.cadence.core.domain.model.MenstrualFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    ) : CalendarUiState {
        fun marksFor(month: YearMonth): Map<LocalDate, DayMark> = CalendarMarks.forMonth(month, flowByDay, prediction)

        /** Past days and today can be toggled; future days can only be cleared (e.g. a pre-filled period). */
        fun canToggle(date: LocalDate): Boolean = !date.isAfter(today) || isLogged(date)

        fun isLogged(date: LocalDate): Boolean = flowByDay[date]?.countsAsPeriod == true
    }
}

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val periods: PeriodRepository,
        settings: SettingsRepository,
        dates: DateSource,
    ) : ViewModel() {
        val uiState: StateFlow<CalendarUiState> =
            combine(periods.flowByDay, settings.settings, dates.today) { flowByDay, s, today ->
                val prediction = CyclePredictor.predict(CycleHistory.from(flowByDay), s.cycleDefaults, today)
                CalendarUiState.Ready(today, flowByDay, prediction)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState.Loading)

        fun toggle(date: LocalDate) {
            val state = uiState.value as? CalendarUiState.Ready ?: return
            if (!state.canToggle(date)) return
            viewModelScope.launch {
                if (state.isLogged(date)) periods.clearPeriodDays(listOf(date)) else periods.logPeriodDays(listOf(date))
            }
        }
    }
