package app.cadence.core.domain.reminders

import java.time.LocalTime

/** Which reminders are on and when (docs/PLAN.md §2.6). All are off until the user turns them on. */
data class ReminderSettings(
    val periodEnabled: Boolean = false,
    /** Remind this many days before the expected start. */
    val periodDaysBefore: Int = DEFAULT_DAYS_BEFORE,
    /** Time of day for the period and late-period reminders. */
    val periodTime: LocalTime = DEFAULT_PERIOD_TIME,
    val lateEnabled: Boolean = false,
    val dailyEnabled: Boolean = false,
    val dailyTime: LocalTime = DEFAULT_DAILY_TIME,
    /** Neutral notification text ("Time to check in") so nothing personal shows. On by default. */
    val discreet: Boolean = true,
) {
    init {
        require(periodDaysBefore in DAYS_BEFORE_RANGE) { "periodDaysBefore must be in $DAYS_BEFORE_RANGE" }
    }

    val anyEnabled: Boolean
        get() = periodEnabled || lateEnabled || dailyEnabled

    companion object {
        val DAYS_BEFORE_RANGE = 1..7
        const val DEFAULT_DAYS_BEFORE = 2
        val DEFAULT_PERIOD_TIME: LocalTime = LocalTime.of(9, 0)
        val DEFAULT_DAILY_TIME: LocalTime = LocalTime.of(20, 0)
    }
}
