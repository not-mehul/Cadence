package app.cadence.core.domain.patterns

import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.d
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CycleDayHeatmapTest {
    // Four periods from 1 Jan 2026: cycles of 28, 28 and 30 days; periods of 5, 5, 4 days.
    private val flows: Map<LocalDate, MenstrualFlow> =
        periodDays(d(2026, 1, 1), 5) + periodDays(d(2026, 1, 29), 5) + periodDays(d(2026, 2, 26), 4) +
            periodDays(d(2026, 3, 28), 5)
    private val history = CycleHistory.from(flows)

    @Test
    fun `counts cycles per cycle day, busiest items first`() {
        val items =
            mapOf(
                // Cramps on day 1 of every cycle and day 2 of two of them.
                d(2026, 1, 1) to setOf("cramps"),
                d(2026, 1, 2) to setOf("cramps"),
                d(2026, 1, 29) to setOf("cramps", "calm"),
                d(2026, 1, 30) to setOf("cramps"),
                d(2026, 2, 26) to setOf("cramps"),
                // Acne on day 21 once; logged twice the same day counts once.
                d(2026, 1, 21) to setOf("acne"),
            )

        val heatmap = CycleDayHeatmap.build(history, items)!!

        assertEquals(3, heatmap.cycles)
        assertEquals(30, heatmap.days, "the longest cycle")
        assertEquals(5, heatmap.periodDays)
        assertEquals(listOf("cramps", "acne", "calm"), heatmap.rows.map { it.itemId })
        val cramps = heatmap.rows.first()
        assertEquals(listOf(3, 2) + List(28) { 0 }, cramps.cyclesByDay)
        assertEquals(5, cramps.total)
        assertEquals(listOf(1, 2), cramps.busiestDays())
        assertEquals(listOf(21), heatmap.rows[1].busiestDays())
    }

    @Test
    fun `needs two cycles with logs, and a row with nothing has no busy days`() {
        assertNull(CycleDayHeatmap.build(history, mapOf(d(2026, 1, 3) to setOf("cramps"))))
        assertEquals(emptyList(), HeatmapRow("x", List(3) { 0 }).busiestDays())
        assertEquals(emptyList(), HeatmapRow("x", emptyList()).busiestDays())
    }

    @Test
    fun `long cycles are cut to the column limit, and only recent cycles count`() {
        val long =
            (0 until 8).fold(emptyMap<LocalDate, MenstrualFlow>()) { acc, c ->
                acc + periodDays(d(2025, 1, 1).plusDays(c * 45L), 5)
            }
        val days = (0 until 8).associate { c -> d(2025, 1, 1).plusDays(c * 45L + 42) to setOf("late") }
        val heatmap = CycleDayHeatmap.build(CycleHistory.from(long), days)!!
        assertEquals(CycleDayHeatmap.MAX_DAYS, heatmap.days)
        assertEquals(CycleDayHeatmap.MAX_CYCLES, heatmap.cycles)
        assertEquals(emptyList(), heatmap.rows, "day 43 is past the last column")
        assertEquals(CycleDayHeatmap.MAX_ROWS, 6)
    }
}
