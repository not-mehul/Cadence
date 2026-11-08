package app.cadence.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.CustomTagRepository
import app.cadence.core.data.repository.DataEraser
import app.cadence.core.data.repository.MedicationRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.security.AppLock
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val dataEraser: DataEraser,
        private val appLock: AppLock,
        private val medications: MedicationRepository,
        private val tags: CustomTagRepository,
        dates: DateSource,
    ) : ViewModel() {
        /** Null while loading. */
        val settings: StateFlow<UserSettings?> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Null while loading. */
        val tracking: StateFlow<TrackingSettingsState?> =
            combine(dates.today, medications.medications, tags.tags, ::TrackingSettingsState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Null removes the schedule (the user's birth control isn't a pill, patch or ring). */
        fun setContraception(schedule: ContraceptiveSchedule?) = update { it.copy(contraception = schedule) }

        fun saveMedication(medication: Medication) {
            viewModelScope.launch { medications.save(medication) }
        }

        fun deleteMedication(id: Long) {
            viewModelScope.launch { medications.delete(id) }
        }

        fun createTag(label: String) {
            viewModelScope.launch { tags.create(label) }
        }

        fun renameTag(
            id: Long,
            label: String,
        ) {
            viewModelScope.launch { tags.rename(id, label) }
        }

        fun deleteTag(id: Long) {
            viewModelScope.launch { tags.delete(id) }
        }

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
            /**
             * The schedule for what the user chose, or null for no pill, patch or ring. Reminder
             * settings carry over. When the layout or pack day changes, missed-dose prompts start
             * afresh from [today], so days under the old layout are never reported.
             */
            fun scheduleFrom(
                choice: BirthControlChoice,
                today: LocalDate,
                previous: ContraceptiveSchedule?,
            ): ContraceptiveSchedule? {
                val method = choice.method ?: return null
                val schedule =
                    ContraceptiveSchedule(
                        method = method,
                        packStart = today.minusDays(choice.dayToday - 1L),
                        activeDays = choice.activeDays,
                        breakDays = choice.breakDays,
                        placebo = choice.placebo && method == ContraceptiveMethod.PILL && choice.breakDays > 0,
                        since = today,
                        reminderEnabled = previous?.reminderEnabled ?: false,
                        reminderTime = previous?.reminderTime ?: ContraceptiveSchedule.DEFAULT_REMINDER_TIME,
                    )
                val unchanged =
                    previous != null &&
                        previous.copy(since = today, packStart = previous.packStartFor(today)) ==
                        schedule.copy(packStart = schedule.packStartFor(today))
                return if (unchanged) previous else schedule
            }

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
