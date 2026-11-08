package app.cadence.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import java.time.LocalTime

/**
 * Single-row table holding [UserSettings]. Settings live in the encrypted database (not
 * plain DataStore) because some, like using hormonal birth control, are sensitive.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "goal")
    val goal: Goal,
    @ColumnInfo(name = "cycle_length")
    val cycleLength: Int,
    @ColumnInfo(name = "period_length")
    val periodLength: Int,
    @ColumnInfo(name = "luteal_phase_length")
    val lutealPhaseLength: Int,
    @ColumnInfo(name = "theme")
    val theme: ThemePreference,
    @ColumnInfo(name = "onboarding_complete")
    val onboardingComplete: Boolean,
    /** Comma-separated [LogCategory] names. Added in schema v3. */
    @ColumnInfo(name = "hidden_categories", defaultValue = "")
    val hiddenCategories: String = "",
    // Reminders, added in schema v5. Times are minutes after midnight.
    @ColumnInfo(name = "period_reminder_enabled", defaultValue = "0")
    val periodReminderEnabled: Boolean = false,
    @ColumnInfo(name = "period_reminder_days_before", defaultValue = "2")
    val periodReminderDaysBefore: Int = ReminderSettings.DEFAULT_DAYS_BEFORE,
    @ColumnInfo(name = "period_reminder_minute", defaultValue = "540")
    val periodReminderMinute: Int = 540,
    @ColumnInfo(name = "late_reminder_enabled", defaultValue = "0")
    val lateReminderEnabled: Boolean = false,
    @ColumnInfo(name = "daily_reminder_enabled", defaultValue = "0")
    val dailyReminderEnabled: Boolean = false,
    @ColumnInfo(name = "daily_reminder_minute", defaultValue = "1200")
    val dailyReminderMinute: Int = 1200,
    @ColumnInfo(name = "discreet_notifications", defaultValue = "1")
    val discreetNotifications: Boolean = true,
) {
    fun toModel(): UserSettings =
        UserSettings(
            goal = goal,
            cycleDefaults = CycleDefaults(cycleLength, periodLength),
            lutealPhaseLength = lutealPhaseLength,
            theme = theme,
            onboardingComplete = onboardingComplete,
            hiddenCategories = LogCategory.parseSet(hiddenCategories),
            reminders =
                ReminderSettings(
                    periodEnabled = periodReminderEnabled,
                    periodDaysBefore = periodReminderDaysBefore.coerceIn(ReminderSettings.DAYS_BEFORE_RANGE),
                    periodTime = minuteToTime(periodReminderMinute),
                    lateEnabled = lateReminderEnabled,
                    dailyEnabled = dailyReminderEnabled,
                    dailyTime = minuteToTime(dailyReminderMinute),
                    discreet = discreetNotifications,
                ),
        )

    companion object {
        const val SINGLETON_ID = 0

        fun from(settings: UserSettings) =
            SettingsEntity(
                goal = settings.goal,
                cycleLength = settings.cycleDefaults.cycleLength,
                periodLength = settings.cycleDefaults.periodLength,
                lutealPhaseLength = settings.lutealPhaseLength,
                theme = settings.theme,
                onboardingComplete = settings.onboardingComplete,
                hiddenCategories = LogCategory.formatSet(settings.hiddenCategories),
                periodReminderEnabled = settings.reminders.periodEnabled,
                periodReminderDaysBefore = settings.reminders.periodDaysBefore,
                periodReminderMinute = timeToMinute(settings.reminders.periodTime),
                lateReminderEnabled = settings.reminders.lateEnabled,
                dailyReminderEnabled = settings.reminders.dailyEnabled,
                dailyReminderMinute = timeToMinute(settings.reminders.dailyTime),
                discreetNotifications = settings.reminders.discreet,
            )

        private fun timeToMinute(time: LocalTime) = time.hour * 60 + time.minute

        private fun minuteToTime(minute: Int): LocalTime = LocalTime.of((minute / 60).coerceIn(0, 23), minute % 60)
    }
}
