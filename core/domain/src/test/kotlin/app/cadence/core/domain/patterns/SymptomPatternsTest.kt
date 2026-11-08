package app.cadence.core.domain.patterns

import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.d
import app.cadence.core.domain.regularHistory
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class SymptomPatternsTest {
    // Five-day periods every 28 days from 5 January 2026: 4 completed cycles (5 Jan, 2 Feb, 2 Mar, 30 Mar), next 27 Apr.
    private val starts = (0L until 5L).map { d(2026, 1, 5).plusDays(it * 28) }
    private val history = CycleHistory.from(regularHistory(starts.first(), 5, 28))

    /** [item] logged on cycle day [day] (1-based) of each of the given cycles. */
    private fun onCycleDay(
        item: String,
        day: Int,
        cycles: List<Int> = listOf(0, 1, 2, 3),
    ): Map<LocalDate, Set<String>> = cycles.associate { starts[it].plusDays(day - 1L) to setOf(item) }

    /** [item] logged [days] days before the start of the following period, in each of the given cycles. */
    private fun beforeNext(
        item: String,
        days: Int,
        cycles: List<Int> = listOf(0, 1, 2, 3),
    ): Map<LocalDate, Set<String>> = cycles.associate { starts[it + 1].minusDays(days.toLong()) to setOf(item) }

    private fun merge(vararg maps: Map<LocalDate, Set<String>>): Map<LocalDate, Set<String>> =
        maps.fold(mutableMapOf<LocalDate, Set<String>>()) { acc, m ->
            m.forEach { (k, v) -> acc[k] = acc[k].orEmpty() + v }
            acc
        }

    @Test
    fun `cramps during the period and headaches before it`() {
        val items =
            merge(
                onCycleDay("cramps", 1),
                onCycleDay("cramps", 2, listOf(1)),
                beforeNext("headache", 2, listOf(0, 1, 3)),
            )

        assertEquals(
            listOf(
                SymptomPattern("cramps", PatternTiming.DURING_PERIOD, null, cycles = 4, outOf = 4),
                SymptomPattern("headache", PatternTiming.BEFORE_PERIOD, 2, cycles = 3, outOf = 4),
            ),
            SymptomPatterns.find(history, items),
        )
    }

    @Test
    fun `around the same cycle day, allowing a day either side`() {
        val items =
            merge(
                onCycleDay("tag:3", 14, listOf(0, 1)),
                onCycleDay("tag:3", 15, listOf(2)),
                onCycleDay("tag:3", 13, listOf(3)),
            )

        assertEquals(
            listOf(SymptomPattern("tag:3", PatternTiming.AROUND_CYCLE_DAY, 14, cycles = 4, outOf = 4)),
            SymptomPatterns.find(history, items),
        )
    }

    @Test
    fun `the typical day before the period is the median`() {
        val items =
            merge(
                beforeNext("acne", 1, listOf(0)),
                beforeNext("acne", 2, listOf(1)),
                beforeNext("acne", 4, listOf(2)),
                beforeNext("acne", 5, listOf(3)),
            )

        assertEquals(3, SymptomPatterns.find(history, items).single().day)
    }

    @Test
    fun `too few cycles, too rare, or cycles without logs`() {
        // Two cycles only.
        assertEquals(emptyList(), SymptomPatterns.find(history, onCycleDay("cramps", 1, listOf(0, 1))))
        // Three of six logged cycles... fine; but only 3 of 4 logged cycles with 2 matching isn't.
        val rare = merge(onCycleDay("calm", 10), onCycleDay("cramps", 1, listOf(0, 1)))
        assertEquals(listOf("calm"), SymptomPatterns.find(history, rare).map { it.itemId })
        // Logs outside completed cycles (the current one) don't count.
        assertEquals(emptyList(), SymptomPatterns.find(history, mapOf(d(2026, 4, 28) to setOf("cramps"))))
    }

    /** [loggedCycles] completed cycles with calm logged on day 10, and a headache on day 20 of the first [headacheCycles]. */
    private fun headachePatterns(
        loggedCycles: Int,
        headacheCycles: Int,
    ): List<SymptomPattern> {
        val periodStarts = (0L..loggedCycles).map { d(2026, 1, 5).plusDays(it * 28) }
        val longer = CycleHistory.from(regularHistory(periodStarts.first(), loggedCycles + 1, 28))
        val calm = (0 until loggedCycles).associate { periodStarts[it].plusDays(9) to setOf("calm") }
        val headaches = (0 until headacheCycles).associate { periodStarts[it].plusDays(19) to setOf("headache") }
        return SymptomPatterns.find(longer, merge(calm, headaches)).filter { it.itemId == "headache" }
    }

    @Test
    fun `a pattern must cover at least half of the logged cycles`() {
        assertEquals(
            listOf(SymptomPattern("headache", PatternTiming.AROUND_CYCLE_DAY, 20, cycles = 3, outOf = 6)),
            headachePatterns(loggedCycles = 6, headacheCycles = 3),
        )
        assertEquals(emptyList(), headachePatterns(loggedCycles = 7, headacheCycles = 3))
    }

    @Test
    fun `excluded cycles are left out`() {
        val excluding =
            CycleHistory.from(
                regularHistory(starts.first(), 5, 28),
                excludedStarts = setOf(starts[0], starts[1]),
            )
        assertEquals(emptyList(), SymptomPatterns.find(excluding, onCycleDay("cramps", 1)))
    }
}
