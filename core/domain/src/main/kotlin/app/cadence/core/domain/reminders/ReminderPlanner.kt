package app.cadence.core.domain.reminders

import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import java.time.LocalDate
import java.time.LocalDateTime

enum class ReminderKind {
    /** The period is expected in a few days. */
    UPCOMING_PERIOD,

    /** The period is past the end of its likely range. */
    LATE_PERIOD,

    /** Daily prompt to log symptoms and mood. */
    DAILY_LOG,
}

/** The next time a reminder should fire, in local wall-clock time. */
data class PlannedReminder(
    val kind: ReminderKind,
    val at: LocalDateTime,
    /** For the upcoming-period reminder: days until the expected start. */
    val daysUntilPeriod: Int? = null,
)

/**
 * Works out the next occurrence of each enabled reminder. Only future times are returned;
 * a reminder whose moment has passed is simply not planned. The app re-plans whenever
 * data or settings change and after each reminder fires.
 */
object ReminderPlanner {
    fun plan(
        flowByDay: Map<LocalDate, MenstrualFlow>,
        settings: UserSettings,
        excludedCycleStarts: Set<LocalDate>,
        now: LocalDateTime,
    ): List<PlannedReminder> {
        val reminders = settings.reminders
        val today = now.toLocalDate()
        val history = CycleHistory.from(flowByDay, excludedCycleStarts)
        val prediction = CyclePredictor.predict(history, settings.cycleDefaults, today)
        val onPeriod = history.latestPeriodStartedBy(today)?.let { today in it } == true

        return buildList {
            if (reminders.periodEnabled && prediction != null && !onPeriod && prediction.daysLate == 0) {
                val at =
                    prediction.expectedNextStart
                        .minusDays(
                            reminders.periodDaysBefore.toLong(),
                        ).atTime(reminders.periodTime)
                if (at.isAfter(now)) add(PlannedReminder(ReminderKind.UPCOMING_PERIOD, at, reminders.periodDaysBefore))
            }
            if (reminders.lateEnabled && prediction != null && !onPeriod) {
                // The first day the period counts as late: the day after its likely range ends.
                val at = prediction.latestNextStart.plusDays(1).atTime(reminders.periodTime)
                if (at.isAfter(now)) add(PlannedReminder(ReminderKind.LATE_PERIOD, at))
            }
            if (reminders.dailyEnabled) {
                val todayAt = today.atTime(reminders.dailyTime)
                add(PlannedReminder(ReminderKind.DAILY_LOG, if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)))
            }
        }
    }
}
