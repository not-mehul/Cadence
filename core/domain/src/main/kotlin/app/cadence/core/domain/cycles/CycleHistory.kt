package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.Cycle
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.Period
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Periods and cycles derived from per-day flow logs (docs/PLAN.md §4).
 *
 * - Consecutive days with light, medium or heavy flow form one period.
 * - A single day without flow between two flow days is bridged (some people skip a day mid-period).
 * - Spotting is logged but never starts or lengthens a period.
 */
class CycleHistory private constructor(
    /** All periods, oldest first. */
    val periods: List<Period>,
    excludedStarts: Set<LocalDate>,
) {
    /** Completed cycles (period start to next period start), oldest first. */
    val cycles: List<Cycle> =
        periods.zipWithNext {
            a,
            b,
            ->
            Cycle(a.start, b.start, excluded = a.start in excludedStarts)
        }

    /** Completed cycles that count towards predictions and statistics. */
    val includedCycles: List<Cycle>
        get() = cycles.filterNot { it.excluded }

    /** The most recent period that has started on or before [date], or null. */
    fun latestPeriodStartedBy(date: LocalDate): Period? = periods.lastOrNull { !it.start.isAfter(date) }

    fun isPeriodDay(date: LocalDate): Boolean = periods.any { date in it }

    companion object {
        /** Days without flow that can sit inside one period. */
        const val MAX_BRIDGED_GAP_DAYS = 1

        val EMPTY = CycleHistory(emptyList(), emptySet())

        /** [excludedStarts]: first days of cycles the user excluded from predictions. */
        fun from(
            flowByDay: Map<LocalDate, MenstrualFlow>,
            excludedStarts: Set<LocalDate> = emptySet(),
        ): CycleHistory {
            val days = flowByDay.filterValues { it.countsAsPeriod }.keys.sorted()
            if (days.isEmpty()) return EMPTY

            val periods = mutableListOf<Period>()
            var start = days.first()
            var end = start
            for (day in days.drop(1)) {
                if (ChronoUnit.DAYS.between(end, day) <= MAX_BRIDGED_GAP_DAYS + 1) {
                    end = day
                } else {
                    periods += Period(start, end)
                    start = day
                    end = day
                }
            }
            periods += Period(start, end)
            return CycleHistory(periods, excludedStarts)
        }
    }
}
