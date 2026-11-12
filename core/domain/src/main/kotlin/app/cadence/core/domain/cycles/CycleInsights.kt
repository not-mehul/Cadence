package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.Period
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Summary statistics over recent cycles. */
data class CycleStats(
    /** Completed, included, plausible cycles the cycle figures are based on (up to six). */
    val cyclesCounted: Int,
    val averageCycleLength: Double?,
    val shortestCycle: Int?,
    val longestCycle: Int?,
    /** Longest minus shortest cycle. */
    val variation: Int?,
    /** Completed periods the period figure is based on (up to six). */
    val periodsCounted: Int,
    val averagePeriodLength: Double?,
)

/**
 * Patterns worth mentioning to a clinician, based on FIGO's normal ranges for menstrual
 * bleeding. Informational only: never a diagnosis.
 */
sealed interface HealthFlag {
    data class ShortCycles(
        val averageDays: Int,
    ) : HealthFlag

    data class LongCycles(
        val averageDays: Int,
    ) : HealthFlag

    data class IrregularCycles(
        val variationDays: Int,
    ) : HealthFlag

    data class LongPeriod(
        val lengthDays: Int,
        val start: LocalDate,
    ) : HealthFlag

    data class NoRecentPeriod(
        val daysSinceLastPeriod: Int,
    ) : HealthFlag

    data class SeverePain(
        val cycles: Int,
    ) : HealthFlag
}

object CycleInsights {
    /** FIGO: normal cycle frequency is 24–38 days... */
    val NORMAL_CYCLE_RANGE = 24..38

    /** ...cycle-to-cycle variation up to 7–9 days (we flag above the upper end)... */
    const val MAX_NORMAL_VARIATION = 9

    /** ...and periods up to 8 days. */
    const val MAX_NORMAL_PERIOD = 8

    /** Cycle-length flags need this many cycles, so one odd cycle doesn't raise a flag. */
    const val MIN_CYCLES_FOR_FLAGS = 3

    /** No period for this long (and not on hormonal birth control). */
    const val NO_PERIOD_DAYS = 90

    /** Pain at or above this level on at least [SEVERE_PAIN_DAYS] period days, in [SEVERE_PAIN_CYCLES]+ cycles. */
    const val SEVERE_PAIN_LEVEL = 7
    const val SEVERE_PAIN_DAYS = 2
    const val SEVERE_PAIN_CYCLES = 3

    fun stats(
        history: CycleHistory,
        today: LocalDate,
    ): CycleStats {
        val cycles = recentCycleLengths(history)
        val periods = recentCompletedPeriods(history, today).map(Period::length)
        return CycleStats(
            cyclesCounted = cycles.size,
            averageCycleLength = cycles.takeIf { it.isNotEmpty() }?.average(),
            shortestCycle = cycles.minOrNull(),
            longestCycle = cycles.maxOrNull(),
            variation = cycles.takeIf { it.isNotEmpty() }?.let { it.max() - it.min() },
            periodsCounted = periods.size,
            averagePeriodLength = periods.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    fun healthFlags(
        history: CycleHistory,
        painByDay: Map<LocalDate, Int>,
        goal: Goal,
        today: LocalDate,
    ): List<HealthFlag> =
        buildList {
            val cycles = recentCycleLengths(history)
            if (cycles.size >= MIN_CYCLES_FOR_FLAGS) {
                val average = cycles.average()
                if (average < NORMAL_CYCLE_RANGE.first) add(HealthFlag.ShortCycles(Math.round(average).toInt()))
                if (average > NORMAL_CYCLE_RANGE.last) add(HealthFlag.LongCycles(Math.round(average).toInt()))
                val variation = cycles.max() - cycles.min()
                if (variation > MAX_NORMAL_VARIATION) add(HealthFlag.IrregularCycles(variation))
            }

            recentCompletedPeriods(history, today)
                .filter { it.length > MAX_NORMAL_PERIOD }
                .maxByOrNull { it.length }
                ?.let { add(HealthFlag.LongPeriod(it.length, it.start)) }

            val latest = history.latestPeriodStartedBy(today)
            if (goal != Goal.BIRTH_CONTROL && latest != null) {
                val days = ChronoUnit.DAYS.between(latest.start, today).toInt()
                if (days >= NO_PERIOD_DAYS) add(HealthFlag.NoRecentPeriod(days))
            }

            val painfulCycles =
                history.periods
                    .filter { !it.start.isAfter(today) }
                    .takeLast(CyclePredictor.HISTORY_WINDOW)
                    .count { period ->
                        period.days().count { (painByDay[it] ?: 0) >= SEVERE_PAIN_LEVEL } >=
                            SEVERE_PAIN_DAYS
                    }
            if (painfulCycles >= SEVERE_PAIN_CYCLES) add(HealthFlag.SeverePain(painfulCycles))
        }

    private fun recentCycleLengths(history: CycleHistory): List<Int> =
        history.includedCycles
            .map { it.length }
            .filter { it in CycleDefaults.CYCLE_LENGTH_RANGE }
            .takeLast(CyclePredictor.HISTORY_WINDOW)

    private fun recentCompletedPeriods(
        history: CycleHistory,
        today: LocalDate,
    ): List<Period> = history.periods.filter { it.end.isBefore(today) }.takeLast(CyclePredictor.HISTORY_WINDOW)
}
