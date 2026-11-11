package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.Period
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where a calendar day sits in its cycle. */
data class CycleDayInfo(
    /** 1 on the first day of a period. */
    val day: Int,
    /** True when counted from a predicted period start rather than a logged one. */
    val estimated: Boolean,
)

/** Facts about any calendar day, past or future, for the calendar's day details. */
object CalendarDays {
    /**
     * Day N of the cycle [date] falls in. It counts from the latest logged period start on or
     * before [date]; for days after [today], a predicted start in between takes over (and the
     * result is an estimate). A predicted start that has already passed without a period doesn't
     * count: the cycle simply runs long, as on Today. Null before the first logged period.
     */
    fun cycleDay(
        date: LocalDate,
        today: LocalDate,
        history: CycleHistory,
        prediction: CyclePrediction?,
    ): CycleDayInfo? {
        val logged = history.latestPeriodStartedBy(date) ?: return null
        val predictedStart =
            prediction?.let { p ->
                p
                    .upcomingPeriods(CalendarMarks.MAX_PREDICTED_CYCLES)
                    .map(Period::start)
                    .lastOrNull { it.isAfter(today) && !it.isAfter(date) }
            }
        // A period already logged ahead (pre-filled by "Period started") beats an earlier prediction.
        val estimated = predictedStart != null && predictedStart.isAfter(logged.start)
        val start = if (estimated) predictedStart else logged.start
        return CycleDayInfo(ChronoUnit.DAYS.between(start, date).toInt() + 1, estimated)
    }

    /** The logged period [date] falls in (a bridged gap counts as inside it), or null. */
    fun periodContaining(
        history: CycleHistory,
        date: LocalDate,
    ): Period? = history.periods.firstOrNull { date in it }
}
