package app.cadence.core.domain.cycles

import app.cadence.core.domain.d
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CycleInsightsTest {
    private fun history(
        firstStart: LocalDate,
        lengths: List<Int>,
        periodLength: Int = 5,
        excluded: Set<LocalDate> = emptySet(),
    ): Pair<CycleHistory, LocalDate> {
        var start = firstStart
        var days: Map<LocalDate, MenstrualFlow> = periodDays(start, periodLength)
        lengths.forEach {
            start = start.plusDays(it.toLong())
            days = days + periodDays(start, periodLength)
        }
        return CycleHistory.from(days, excluded) to start
    }

    private fun flags(
        history: CycleHistory,
        today: LocalDate,
        goal: Goal = Goal.TRACK,
        pain: Map<LocalDate, Int> = emptyMap(),
    ) = CycleInsights.healthFlags(history, pain, goal, today)

    @Test
    fun `no data, no stats`() {
        val stats = CycleInsights.stats(CycleHistory.EMPTY, d(2026, 3, 1))
        assertEquals(CycleStats(0, null, null, null, null, 0, null), stats)
    }

    @Test
    fun `stats over recent cycles and completed periods`() {
        val (h, last) = history(d(2026, 1, 1), listOf(27, 29, 31), periodLength = 4)
        val stats = CycleInsights.stats(h, last.plusDays(1))

        assertEquals(3, stats.cyclesCounted)
        assertEquals(29.0, stats.averageCycleLength)
        assertEquals(27, stats.shortestCycle)
        assertEquals(31, stats.longestCycle)
        assertEquals(4, stats.variation)
        assertEquals(3, stats.periodsCounted, "the period still in progress isn't counted")
        assertEquals(4.0, stats.averagePeriodLength)
    }

    @Test
    fun `excluded cycles are left out of stats`() {
        val (h, last) = history(d(2026, 1, 1), listOf(28, 55, 28), excluded = setOf(d(2026, 1, 29)))
        val stats = CycleInsights.stats(h, last.plusDays(1))
        assertEquals(2, stats.cyclesCounted)
        assertEquals(28, stats.longestCycle)
    }

    @Test
    fun `implausible cycles (logging gaps) are left out of stats`() {
        // A 10-day and a 70-day "cycle" are logging mistakes or gaps, not real cycles.
        val (h, last) = history(d(2025, 6, 1), listOf(28, 10, 70, 30), periodLength = 2)
        val stats = CycleInsights.stats(h, last.plusDays(1))
        assertEquals(2, stats.cyclesCounted)
        assertEquals(29.0, stats.averageCycleLength)
    }

    @Test
    fun `regular cycles raise no flags`() {
        val (h, last) = history(d(2026, 1, 1), listOf(28, 30, 27, 29))
        assertEquals(emptyList(), flags(h, last.plusDays(10)))
    }

    @Test
    fun `cycle flags need three cycles`() {
        val (h, last) = history(d(2026, 1, 1), listOf(20, 20))
        assertEquals(emptyList(), flags(h, last.plusDays(1)))
    }

    @Test
    fun `short and long cycles`() {
        val (short, s) = history(d(2026, 1, 1), listOf(21, 22, 22))
        assertEquals(listOf<HealthFlag>(HealthFlag.ShortCycles(22)), flags(short, s.plusDays(5)))

        val (long, l) = history(d(2025, 1, 1), listOf(40, 42, 41))
        assertEquals(listOf<HealthFlag>(HealthFlag.LongCycles(41)), flags(long, l.plusDays(5)))
    }

    @Test
    fun `boundaries of the normal range don't flag`() {
        val (h, last) = history(d(2026, 1, 1), listOf(24, 24, 24))
        assertEquals(emptyList(), flags(h, last.plusDays(5)))
        val (h2, last2) = history(d(2025, 1, 1), listOf(38, 38, 38))
        assertEquals(emptyList(), flags(h2, last2.plusDays(5)))
    }

    @Test
    fun `variation over nine days is irregular, nine is not`() {
        val (h, last) = history(d(2026, 1, 1), listOf(25, 35, 30))
        assertEquals(listOf<HealthFlag>(HealthFlag.IrregularCycles(10)), flags(h, last.plusDays(5)))
        val (h2, last2) = history(d(2026, 1, 1), listOf(25, 34, 30))
        assertEquals(emptyList(), flags(h2, last2.plusDays(5)))
    }

    @Test
    fun `a period over eight days`() {
        val (h, last) = history(d(2026, 1, 1), listOf(30), periodLength = 9)
        assertEquals(listOf<HealthFlag>(HealthFlag.LongPeriod(9, d(2026, 1, 1))), flags(h, last.plusDays(10)))
    }

    @Test
    fun `no period for ninety days, unless on birth control`() {
        val (h, last) = history(d(2026, 1, 1), emptyList())
        assertEquals(emptyList(), flags(h, last.plusDays(89)))
        assertEquals(listOf<HealthFlag>(HealthFlag.NoRecentPeriod(90)), flags(h, last.plusDays(90)))
        assertEquals(emptyList(), flags(h, last.plusDays(120), goal = Goal.BIRTH_CONTROL))
        assertEquals(emptyList(), flags(CycleHistory.EMPTY, last))
    }

    @Test
    fun `severe pain on two or more period days in three cycles`() {
        val (h, last) = history(d(2026, 1, 1), listOf(28, 28))
        val starts = h.periods.map { it.start }
        val twoBadDays = starts.flatMap { listOf(it to 8, it.plusDays(1) to 7) }.toMap()
        assertEquals(listOf<HealthFlag>(HealthFlag.SeverePain(3)), flags(h, last.plusDays(10), pain = twoBadDays))

        val oneBadDay = starts.associateWith { 9 }
        assertTrue(flags(h, last.plusDays(10), pain = oneBadDay).none { it is HealthFlag.SeverePain })
    }
}
