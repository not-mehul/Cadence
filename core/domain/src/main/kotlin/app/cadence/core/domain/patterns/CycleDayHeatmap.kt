package app.cadence.core.domain.patterns

import app.cadence.core.domain.cycles.CycleHistory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One item's row: in how many of the cycles it was logged on each cycle day. */
data class HeatmapRow(
    val itemId: String,
    /** Index 0 is cycle day 1. */
    val cyclesByDay: List<Int>,
) {
    val total: Int get() = cyclesByDay.sum()

    /** Cycle days (1-based) where it shows up in at least half as many cycles as on its busiest day. */
    fun busiestDays(): List<Int> {
        val peak = cyclesByDay.maxOrNull() ?: 0
        if (peak == 0) return emptyList()
        return cyclesByDay.withIndex().filter { it.value * 2 >= peak }.map { it.index + 1 }
    }
}

/** What's logged on each cycle day across recent cycles: the data behind "insights by cycle day". */
data class CycleDayHeatmap(
    /** Completed cycles the counts come from. */
    val cycles: Int,
    /** Cycle days shown: 1 to [days]. */
    val days: Int,
    /** Most logged items first. */
    val rows: List<HeatmapRow>,
    /** Typical period length, to shade the period days. */
    val periodDays: Int,
) {
    companion object {
        /** Needs a few cycles before the grid says anything. */
        const val MIN_CYCLES = 2
        const val MAX_CYCLES = 6
        const val MAX_ROWS = 6

        /** Columns stop here; longer cycles add little and squeeze the grid. */
        const val MAX_DAYS = 40

        /** Null until there are [MIN_CYCLES] completed, included cycles with something logged. */
        fun build(
            history: CycleHistory,
            itemsByDay: Map<LocalDate, Set<String>>,
        ): CycleDayHeatmap? {
            val periodsByStart = history.periods.associateBy { it.start }
            val cycles =
                history.includedCycles
                    .takeLast(MAX_CYCLES)
                    .filter { c -> itemsByDay.keys.any { !it.isBefore(c.start) && it.isBefore(c.nextStart) } }
            if (cycles.size < MIN_CYCLES) return null
            // The longest cycle sets the columns (there are always at least MIN_CYCLES here).
            val days = cycles.fold(0) { longest, c -> maxOf(longest, c.length) }.coerceAtMost(MAX_DAYS)

            // Per item, per day: the set of cycles it was logged in, so a day counts once per cycle.
            val counts = mutableMapOf<String, IntArray>()
            cycles.forEach { cycle ->
                itemsByDay
                    .filterKeys { !it.isBefore(cycle.start) && it.isBefore(cycle.nextStart) }
                    .forEach { (date, items) ->
                        val day = ChronoUnit.DAYS.between(cycle.start, date).toInt()
                        if (day < days) items.forEach { counts.getOrPut(it) { IntArray(days) }[day]++ }
                    }
            }
            val rows =
                counts
                    .map { (id, perDay) -> HeatmapRow(id, perDay.toList()) }
                    .sortedWith(compareByDescending<HeatmapRow> { it.total }.thenBy { it.itemId })
                    .take(MAX_ROWS)
            val periodDays =
                cycles
                    .map { periodsByStart.getValue(it.start).length }
                    .sorted()
                    .let { it[it.size / 2] }
            return CycleDayHeatmap(cycles.size, days, rows, periodDays)
        }
    }
}
