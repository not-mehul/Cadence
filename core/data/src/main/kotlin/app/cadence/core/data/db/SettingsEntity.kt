package app.cadence.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.PackSupply
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.core.domain.security.SecuritySettings
import java.time.LocalDate
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
    // Security, added in schema v6.
    @ColumnInfo(name = "lock_enabled", defaultValue = "0")
    val lockEnabled: Boolean = false,
    @ColumnInfo(name = "lock_timeout_minutes", defaultValue = "0")
    val lockTimeoutMinutes: Int = 0,
    @ColumnInfo(name = "block_screenshots", defaultValue = "1")
    val blockScreenshots: Boolean = true,
    // Birth control schedule, added in schema v7. No method means no schedule.
    @ColumnInfo(name = "bc_method")
    val bcMethod: ContraceptiveMethod? = null,
    @ColumnInfo(name = "bc_pack_start")
    val bcPackStart: LocalDate? = null,
    @ColumnInfo(name = "bc_active_days", defaultValue = "21")
    val bcActiveDays: Int = 21,
    @ColumnInfo(name = "bc_break_days", defaultValue = "7")
    val bcBreakDays: Int = 7,
    @ColumnInfo(name = "bc_placebo", defaultValue = "0")
    val bcPlacebo: Boolean = false,
    @ColumnInfo(name = "bc_since")
    val bcSince: LocalDate? = null,
    @ColumnInfo(name = "bc_reminder_enabled", defaultValue = "0")
    val bcReminderEnabled: Boolean = false,
    @ColumnInfo(name = "bc_reminder_minute", defaultValue = "540")
    val bcReminderMinute: Int = 540,
    // Packs on hand for refill reminders, added in schema v8. Null means not tracked.
    @ColumnInfo(name = "bc_supply_packs")
    val bcSupplyPacks: Int? = null,
    @ColumnInfo(name = "bc_supply_counted_on")
    val bcSupplyCountedOn: LocalDate? = null,
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
            security =
                SecuritySettings(
                    lockEnabled = lockEnabled,
                    lockTimeoutMinutes = lockTimeoutMinutes.takeIf { it in SecuritySettings.LOCK_TIMEOUT_OPTIONS } ?: 0,
                    blockScreenshots = blockScreenshots,
                ),
            contraception = contraception(),
        )

    /** Null when no schedule is set up, or when the stored one isn't valid (it then has to be set up again). */
    private fun contraception(): ContraceptiveSchedule? {
        val method = bcMethod ?: return null
        val packStart = bcPackStart ?: return null
        return runCatching {
            ContraceptiveSchedule(
                method = method,
                packStart = packStart,
                activeDays = bcActiveDays,
                breakDays = bcBreakDays,
                placebo = bcPlacebo,
                since = bcSince ?: packStart,
                reminderEnabled = bcReminderEnabled,
                reminderTime = minuteToTime(bcReminderMinute),
                supply = supply(),
            )
        }.getOrNull()
    }

    /** A count out of range is dropped rather than losing the whole schedule. */
    private fun supply(): PackSupply? {
        val packs = bcSupplyPacks ?: return null
        val countedOn = bcSupplyCountedOn ?: return null
        return runCatching { PackSupply(packs, countedOn) }.getOrNull()
    }

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
                lockEnabled = settings.security.lockEnabled,
                lockTimeoutMinutes = settings.security.lockTimeoutMinutes,
                blockScreenshots = settings.security.blockScreenshots,
            ).withContraception(settings.contraception)

        private fun SettingsEntity.withContraception(schedule: ContraceptiveSchedule?): SettingsEntity =
            if (schedule == null) {
                this
            } else {
                copy(
                    bcMethod = schedule.method,
                    bcPackStart = schedule.packStart,
                    bcActiveDays = schedule.activeDays,
                    bcBreakDays = schedule.breakDays,
                    bcPlacebo = schedule.placebo,
                    bcSince = schedule.since,
                    bcReminderEnabled = schedule.reminderEnabled,
                    bcReminderMinute = timeToMinute(schedule.reminderTime),
                    bcSupplyPacks = schedule.supply?.packs,
                    bcSupplyCountedOn = schedule.supply?.countedOn,
                )
            }

        private fun timeToMinute(time: LocalTime) = time.hour * 60 + time.minute

        private fun minuteToTime(minute: Int): LocalTime = LocalTime.of((minute / 60).coerceIn(0, 23), minute % 60)
    }
}
