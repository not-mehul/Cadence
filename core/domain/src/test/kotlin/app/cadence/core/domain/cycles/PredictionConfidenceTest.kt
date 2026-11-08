package app.cadence.core.domain.cycles

import app.cadence.core.domain.d
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.Period
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredictionConfidenceTest {
    private val defaults = CycleDefaults(28, 5)

    /** Periods starting on [firstStart], separated by [lengths]. Returns the flow map and last start. */
    private fun cycles(
        firstStart: LocalDate,
        vararg lengths: Int,
    ): Pair<Map<LocalDate, MenstrualFlow>, LocalDate> {
        var start = firstStart
        var days = periodDays(start, 4)
        lengths.forEach {
            start = start.plusDays(it.toLong())
            days = days + periodDays(start, 4)
        }
        return days to start
    }

    private fun predict(
        days: Map<LocalDate, MenstrualFlow>,
        today: LocalDate,
        excluded: Set<LocalDate> = emptySet(),
    ) = CyclePredictor.predict(CycleHistory.from(days, excluded), defaults, today)!!

    @Test
    fun `recent cycles count more than older ones`() {
        // Plain average of 25, 25, 35 is 28.3; weighted 1:2:3 it is 30.
        val (days, last) = cycles(d(2026, 1, 1), 25, 25, 35)
        assertEquals(30, predict(days, last.plusDays(1)).cycleLength)
    }

    @Test
    fun `steady cycles give high confidence and a narrow range`() {
        val (days, last) = cycles(d(2026, 1, 1), 28, 28, 29)
        val p = predict(days, last.plusDays(1))

        assertEquals(Confidence.HIGH, p.confidence)
        assertEquals(1, p.rangeDays, "SD 0.58 rounds to 1, the minimum")
        assertEquals(p.expectedNextStart.minusDays(1), p.earliestNextStart)
        assertEquals(p.expectedNextStart.plusDays(1), p.latestNextStart)
    }

    @Test
    fun `two steady cycles are only medium confidence`() {
        val (days, last) = cycles(d(2026, 1, 1), 28, 28)
        assertEquals(Confidence.MEDIUM, predict(days, last.plusDays(1)).confidence)
    }

    @Test
    fun `moderate variation is medium confidence`() {
        // 26, 30, 28, 33: SD 2.99.
        val (days, last) = cycles(d(2026, 1, 1), 26, 30, 28, 33)
        val p = predict(days, last.plusDays(1))
        assertEquals(Confidence.MEDIUM, p.confidence)
        assertEquals(3, p.rangeDays)
    }

    @Test
    fun `irregular cycles are low confidence with the range capped`() {
        // 22, 40, 24, 45: SD 11.3, range capped at 7.
        val (days, last) = cycles(d(2026, 1, 1), 22, 40, 24, 45)
        val p = predict(days, last.plusDays(1))
        assertEquals(Confidence.LOW, p.confidence)
        assertEquals(7, p.rangeDays)
    }

    @Test
    fun `typical lengths are low confidence with a three day range`() {
        val p = predict(periodDays(d(2026, 3, 1), 4), d(2026, 3, 5))
        assertEquals(PredictionBasis.TYPICAL, p.basis)
        assertEquals(Confidence.LOW, p.confidence)
        assertEquals(3, p.rangeDays)
    }

    @Test
    fun `excluded cycles are ignored`() {
        // 28, 50 (excluded: illness), 28, 28.
        val (days, last) = cycles(d(2026, 1, 1), 28, 50, 28, 28)
        val excludedStart = d(2026, 1, 29)
        val p = predict(days, last.plusDays(1), excluded = setOf(excludedStart))

        assertEquals(28, p.cycleLength)
        assertEquals(3, p.cyclesUsed)
        assertEquals(Confidence.HIGH, p.confidence)
    }

    @Test
    fun `late means past the end of the range`() {
        val p = predict(periodDays(d(2026, 3, 1), 4), today = d(2026, 4, 1))
        assertEquals(3, p.daysLate)
        assertFalse(p.isLate, "3 days past is still within the 3-day range")
        assertTrue(predict(periodDays(d(2026, 3, 1), 4), today = d(2026, 4, 2)).isLate)
    }

    @Test
    fun `fertile windows end the day after ovulation, 14 days before each period`() {
        val p = predict(periodDays(d(2026, 3, 1), 4), today = d(2026, 3, 5))
        val windows = p.fertileWindows(count = 2, lutealPhaseLength = 14)

        assertEquals(d(2026, 3, 15), windows[0].ovulation)
        assertEquals(Period(d(2026, 3, 10), d(2026, 3, 16)), windows[0].window)
        assertEquals(d(2026, 4, 12), windows[1].ovulation)
    }

    @Test
    fun `once the expected day passes the current cycle's window is dropped`() {
        val p = predict(periodDays(d(2026, 3, 1), 4), today = d(2026, 3, 31))
        val windows = p.fertileWindows(count = 2, lutealPhaseLength = 14)

        assertEquals(1, windows.size)
        assertEquals(d(2026, 3, 31).plusDays(28 - 14), windows[0].ovulation)
    }

    @Test
    fun `weighted average and standard deviation`() {
        assertEquals(30.0, CyclePredictor.weightedAverage(listOf(25, 25, 35)))
        assertEquals(sqrt(2.0), CyclePredictor.standardDeviation(listOf(26, 28)), 1e-9)
    }
}
