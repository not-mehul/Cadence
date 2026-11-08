package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Period
import java.time.LocalDate

/**
 * The day-level edits behind the one-tap actions. Callers apply the result to storage.
 *
 * "Period started" logs the predicted number of days up front, so a period is complete
 * even if the user never taps "Period ended". Ending early trims the extra days.
 */
object PeriodEditor {
    /** Days to log when a period starts on [start] and is expected to last [expectedLength] days. */
    fun daysForStart(
        start: LocalDate,
        expectedLength: Int,
    ): List<LocalDate> {
        val length = expectedLength.coerceIn(CycleDefaults.PERIOD_LENGTH_RANGE)
        return Period(start, start.plusDays(length - 1L)).days()
    }

    /** Days to clear when [period] ended on [lastDay]: everything logged after it. */
    fun daysToClearForEnd(
        period: Period,
        lastDay: LocalDate,
    ): List<LocalDate> {
        require(lastDay in period) { "$lastDay is not in $period" }
        return period.days().filter { it.isAfter(lastDay) }
    }
}
