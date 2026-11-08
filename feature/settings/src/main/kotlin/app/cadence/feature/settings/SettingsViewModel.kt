package app.cadence.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.DataEraser
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.security.AppLock
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val dataEraser: DataEraser,
        private val appLock: AppLock,
    ) : ViewModel() {
        /** Null while loading. */
        val settings: StateFlow<UserSettings?> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun setCycleLength(days: Int) = update { it.copy(cycleDefaults = it.cycleDefaults.copy(cycleLength = days)) }

        fun setPeriodLength(days: Int) = update { it.copy(cycleDefaults = it.cycleDefaults.copy(periodLength = days)) }

        fun setGoal(goal: Goal) = update { it.copy(goal = goal) }

        fun setTheme(theme: ThemePreference) = update { it.copy(theme = theme) }

        fun setHiddenCategories(hidden: Set<LogCategory>) =
            update {
                it.copy(hiddenCategories = hidden.filter { c -> c.canBeHidden }.toSet())
            }

        fun setReminders(reminders: ReminderSettings) = update { it.copy(reminders = reminders) }

        /** Call only after the user has authenticated. */
        fun setLockEnabled(enabled: Boolean) {
            // They just proved who they are, so don't lock them out of the screen they're on.
            if (enabled) appLock.unlock()
            update { it.copy(security = it.security.copy(lockEnabled = enabled)) }
        }

        fun setLockTimeout(minutes: Int) = update { it.copy(security = it.security.copy(lockTimeoutMinutes = minutes)) }

        fun setBlockScreenshots(block: Boolean) =
            update { it.copy(security = it.security.copy(blockScreenshots = block)) }

        /** Erases everything; the app then returns to onboarding. */
        fun deleteAllData() {
            viewModelScope.launch { dataEraser.eraseAll() }
        }

        private fun update(transform: (UserSettings) -> UserSettings) {
            viewModelScope.launch { settingsRepository.update(transform) }
        }

        companion object {
            /** Allowed cycle lengths given the current period length (a period must be shorter than its cycle). */
            fun cycleLengthRange(defaults: CycleDefaults): IntRange {
                val min = maxOf(CycleDefaults.CYCLE_LENGTH_RANGE.first, defaults.periodLength + 1)
                return min..CycleDefaults.CYCLE_LENGTH_RANGE.last
            }

            fun periodLengthRange(defaults: CycleDefaults): IntRange {
                val max = minOf(CycleDefaults.PERIOD_LENGTH_RANGE.last, defaults.cycleLength - 1)
                return CycleDefaults.PERIOD_LENGTH_RANGE.first..max
            }
        }
    }
