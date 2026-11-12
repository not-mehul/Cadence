package app.cadence.core.domain.patterns

import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.model.Cycle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** When in the cycle a pattern shows up. */
enum class PatternTiming {
    /** On period days. */
    DURING_PERIOD,

    /** In the few days before the next period starts. */
    BEFORE_PERIOD,

    /** Around the same cycle day each cycle (outside the period and the days before it). */
    AROUND_CYCLE_DAY,
}

/**
 * Something the user tends to log at the same point in their cycle, such as "headaches
 * in the days before your period". An observation, not a diagnosis.
 */
data class SymptomPattern(
    /** A symptom or mood id, or a custom tag's item id. */
    val itemId: String,
    val timing: PatternTiming,
    /** Typical days before the period for [PatternTiming.BEFORE_PERIOD]; the cycle day for [PatternTiming.AROUND_CYCLE_DAY]. */
    val day: Int?,
    /** Cycles in which it showed up at this point... */
    val cycles: Int,
    /** ...out of the cycles with anything logged. */
    val outOf: Int,
)

object SymptomPatterns {
    /** A pattern needs this many cycles... */
    const val MIN_CYCLES = 3

    /** ...and at least half of the cycles with logs. */
    const val MIN_SHARE = 0.5

    /** Only recent cycles count, so old habits fade out. */
    const val MAX_CYCLES = 12

    /** "Before your period" means within this many days of it. */
    const val BEFORE_PERIOD_DAYS = 5

    /** "Around cycle day N" allows this many days either side. */
    const val CYCLE_DAY_TOLERANCE = 1

    /**
     * Patterns over the recent completed cycles that aren't excluded. [itemsByDay] holds what
     * was logged each day (see [app.cadence.core.domain.log.DayEntry.patternItems]). Cycles
     * with nothing logged don't count against a pattern.
     */
    fun find(
        history: CycleHistory,
        itemsByDay: Map<LocalDate, Set<String>>,
    ): List<SymptomPattern> {
        val periodsByStart = history.periods.associateBy { it.start }
        val cycles =
            history.includedCycles
                .takeLast(MAX_CYCLES)
                .map { cycle -> LoggedCycle(cycle, periodsByStart.getValue(cycle.start).length, itemsByDay) }
                .filter { it.items.isNotEmpty() }
        if (cycles.size < MIN_CYCLES) return emptyList()

        val itemIds = cycles.flatMap { it.items.keys }.toSortedSet()
        return itemIds
            .flatMap { id ->
                listOfNotNull(duringPeriod(id, cycles), beforePeriod(id, cycles), aroundCycleDay(id, cycles))
            }.sortedWith(compareByDescending<SymptomPattern> { it.cycles }.thenBy { it.timing }.thenBy { it.itemId })
    }

    private fun duringPeriod(
        id: String,
        cycles: List<LoggedCycle>,
    ): SymptomPattern? {
        val count = cycles.count { c -> c.days(id).any { c.isPeriodDay(it) } }
        return pattern(id, PatternTiming.DURING_PERIOD, null, count, cycles.size)
    }

    private fun beforePeriod(
        id: String,
        cycles: List<LoggedCycle>,
    ): SymptomPattern? {
        val offsets = cycles.map { c -> c.days(id).filter { c.isBeforePeriod(it) }.map(c::daysBeforeNext) }
        val count = offsets.count { it.isNotEmpty() }
        val typical = offsets.flatten().takeIf { it.isNotEmpty() }?.let(::median)
        return pattern(id, PatternTiming.BEFORE_PERIOD, typical, count, cycles.size)
    }

    private fun aroundCycleDay(
        id: String,
        cycles: List<LoggedCycle>,
    ): SymptomPattern? {
        val daysPerCycle = cycles.map { c -> c.days(id).filter { c.isMidCycle(it) }.map(c::cycleDay) }

        fun cyclesNear(day: Int) = daysPerCycle.count { days -> days.any { abs(it - day) <= CYCLE_DAY_TOLERANCE } }
        // The cycle day that the most cycles have it logged near; the earliest wins a tie.
        val best =
            daysPerCycle
                .flatten()
                .map { day -> day to cyclesNear(day) }
                .maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
                ?: return null
        return pattern(id, PatternTiming.AROUND_CYCLE_DAY, best.first, best.second, cycles.size)
    }

    private fun pattern(
        id: String,
        timing: PatternTiming,
        day: Int?,
        count: Int,
        outOf: Int,
    ): SymptomPattern? =
        if (count >= MIN_CYCLES && count >= outOf * MIN_SHARE) SymptomPattern(id, timing, day, count, outOf) else null

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
    }

    /** One completed cycle and what was logged during it. */
    private class LoggedCycle(
        val cycle: Cycle,
        val periodLength: Int,
        itemsByDay: Map<LocalDate, Set<String>>,
    ) {
        /** Item id to the days it was logged in this cycle. */
        val items: Map<String, List<LocalDate>> =
            generateSequence(cycle.start) { it.plusDays(1) }
                .takeWhile { it.isBefore(cycle.nextStart) }
                .flatMap { day -> itemsByDay[day].orEmpty().map { it to day } }
                .groupBy({ it.first }, { it.second })

        fun days(id: String): List<LocalDate> = items[id].orEmpty()

        fun cycleDay(date: LocalDate): Int = ChronoUnit.DAYS.between(cycle.start, date).toInt() + 1

        fun daysBeforeNext(date: LocalDate): Int = ChronoUnit.DAYS.between(date, cycle.nextStart).toInt()

        fun isPeriodDay(date: LocalDate): Boolean = cycleDay(date) <= periodLength

        fun isBeforePeriod(date: LocalDate): Boolean = !isPeriodDay(date) && daysBeforeNext(date) <= BEFORE_PERIOD_DAYS

        fun isMidCycle(date: LocalDate): Boolean = !isPeriodDay(date) && !isBeforePeriod(date)
    }
}
