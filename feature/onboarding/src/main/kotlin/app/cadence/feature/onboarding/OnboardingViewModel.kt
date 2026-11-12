package app.cadence.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.cycles.PeriodEditor
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.time.TodayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class OnboardingStep { WELCOME, LAST_PERIOD, CYCLE_LENGTH, PERIOD_LENGTH, GOAL }

data class OnboardingState(
    val today: LocalDate,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val lastPeriodStart: LocalDate? = null,
    val cycleLength: Int = CycleDefaults.DEFAULT_CYCLE_LENGTH,
    val periodLength: Int = CycleDefaults.DEFAULT_PERIOD_LENGTH,
    val goal: Goal = Goal.TRACK,
    val finishing: Boolean = false,
) {
    val cycleLengthRange: IntRange
        get() = maxOf(CycleDefaults.CYCLE_LENGTH_RANGE.first, periodLength + 1)..CycleDefaults.CYCLE_LENGTH_RANGE.last
    val periodLengthRange: IntRange
        get() = CycleDefaults.PERIOD_LENGTH_RANGE.first..minOf(CycleDefaults.PERIOD_LENGTH_RANGE.last, cycleLength - 1)

    /** The oldest date accepted for the last period. */
    val earliestLastPeriod: LocalDate
        get() = today.minusYears(1)
}

/**
 * Collects the few answers predictions need. Every question can be
 * skipped; defaults are used instead.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val periods: PeriodRepository,
        private val settings: SettingsRepository,
        todayProvider: TodayProvider,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(OnboardingState(today = todayProvider.today()))
        val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

        fun next() =
            mutableState.update {
                it.copy(step = OnboardingStep.entries[minOf(it.step.ordinal + 1, OnboardingStep.entries.lastIndex)])
            }

        fun back() = mutableState.update { it.copy(step = OnboardingStep.entries[maxOf(it.step.ordinal - 1, 0)]) }

        fun setLastPeriodStart(date: LocalDate?) =
            mutableState.update { s ->
                s.copy(lastPeriodStart = date?.takeIf { !it.isAfter(s.today) && !it.isBefore(s.earliestLastPeriod) })
            }

        fun skipLastPeriod() {
            mutableState.update { it.copy(lastPeriodStart = null) }
            next()
        }

        fun setCycleLength(days: Int) =
            mutableState.update { it.copy(cycleLength = days.coerceIn(it.cycleLengthRange)) }

        fun setPeriodLength(days: Int) =
            mutableState.update { it.copy(periodLength = days.coerceIn(it.periodLengthRange)) }

        /** "Not sure": use the typical value and move on. */
        fun useTypicalCycleLength() {
            mutableState.update { it.copy(cycleLength = CycleDefaults.DEFAULT_CYCLE_LENGTH) }
            next()
        }

        fun useTypicalPeriodLength() {
            mutableState.update {
                it.copy(
                    periodLength = CycleDefaults.DEFAULT_PERIOD_LENGTH.coerceIn(it.periodLengthRange),
                )
            }
            next()
        }

        fun setGoal(goal: Goal) = mutableState.update { it.copy(goal = goal) }

        /** Saves everything. The app leaves onboarding once settings say it is complete. */
        fun finish() {
            val s = mutableState.value
            if (s.finishing) return
            mutableState.update { it.copy(finishing = true) }
            viewModelScope.launch {
                s.lastPeriodStart?.let { periods.logPeriodDays(PeriodEditor.daysForStart(it, s.periodLength)) }
                settings.update {
                    it.copy(
                        goal = s.goal,
                        cycleDefaults = CycleDefaults(s.cycleLength, s.periodLength),
                        onboardingComplete = true,
                    )
                }
            }
        }
    }
