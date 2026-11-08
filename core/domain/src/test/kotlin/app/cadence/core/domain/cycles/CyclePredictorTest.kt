package app.cadence.core.domain.cycles

import app.cadence.core.domain.d
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Period
import app.cadence.core.domain.periodDays
import app.cadence.core.domain.regularHistory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CyclePredictorTest {
    private val defaults = CycleDefaults(cycleLength = 30, periodLength = 4)

    private fun predict(
        days: Map<java.time.LocalDate, app.cadence.core.domain.model.MenstrualFlow>,
        today: java.time.LocalDate,
        defaults: CycleDefaults = this.defaults,
    ) = CyclePredictor.predict(CycleHistory.from(days), defaults, today)

    @Test
    fun `no prediction without a logged period`() {
        assertNull(predict(emptyMap(), d(2026, 3, 1)))
    }

    @Test
    fun `no prediction when the only period is in the future`() {
        assertNull(predict(periodDays(d(2026, 3, 10), 5), d(2026, 3, 1)))
    }

    @Test
    fun `one period uses the typical lengths`() {
        val p = predict(periodDays(d(2026, 3, 1), 6), today = d(2026, 3, 10))!!

        assertEquals(PredictionBasis.TYPICAL, p.basis)
        assertEquals(0, p.cyclesUsed)
        assertEquals(30, p.cycleLength)
        assertEquals(6, p.periodLength, "a completed period is already better than the typical length")
        assertEquals(d(2026, 3, 31), p.expectedNextStart)
        assertEquals(d(2026, 3, 31), p.nextStart)
        assertEquals(0, p.daysLate)
    }

    @Test
    fun `one completed cycle is still not enough history`() {
        val p = predict(regularHistory(d(2026, 1, 1), count = 2, cycleLength = 25), today = d(2026, 2, 10))!!

        assertEquals(PredictionBasis.TYPICAL, p.basis)
        assertEquals(30, p.cycleLength)
    }

    @Test
    fun `two or more cycles use the average of history`() {
        // Cycles of 26 and 29 days → 27.5 → rounds to 28.
        val days = periodDays(d(2026, 1, 1), 5) + periodDays(d(2026, 1, 27), 5) + periodDays(d(2026, 2, 25), 5)
        val p = predict(days, today = d(2026, 3, 5))!!

        assertEquals(PredictionBasis.HISTORY, p.basis)
        assertEquals(2, p.cyclesUsed)
        assertEquals(28, p.cycleLength)
        assertEquals(5, p.periodLength)
        assertEquals(d(2026, 3, 25), p.expectedNextStart)
    }

    @Test
    fun `only the last six cycles count`() {
        // Two old 40-day cycles, then six 28-day cycles.
        val old = periodDays(d(2025, 1, 1), 5) + periodDays(d(2025, 2, 10), 5)
        val recent = regularHistory(d(2025, 3, 22), count = 7, cycleLength = 28)
        val p = predict(old + recent, today = d(2025, 9, 20))!!

        assertEquals(28, p.cycleLength)
        assertEquals(6, p.cyclesUsed)
    }

    @Test
    fun `cycles outside the plausible range are ignored as logging gaps`() {
        // 28, then a 90-day gap (missed logging), then 30.
        val days =
            periodDays(d(2026, 1, 1), 5) + periodDays(d(2026, 1, 29), 5) + periodDays(d(2026, 4, 29), 5) +
                periodDays(d(2026, 5, 29), 5)
        val p = predict(days, today = d(2026, 6, 5))!!

        assertEquals(29, p.cycleLength)
        assertEquals(2, p.cyclesUsed)
    }

    @Test
    fun `an ongoing period is left out of the period length average`() {
        // Completed periods of 4 and 6 days; the current one is pre-filled to 10 days.
        val days = periodDays(d(2026, 1, 1), 4) + periodDays(d(2026, 1, 29), 6) + periodDays(d(2026, 2, 26), 10)
        val p = predict(days, today = d(2026, 2, 27))!!

        assertEquals(5, p.periodLength)
    }

    @Test
    fun `implausibly long periods are left out of the average`() {
        val days = periodDays(d(2026, 1, 1), 20) + periodDays(d(2026, 3, 1), 3)
        val p = predict(days, today = d(2026, 3, 10))!!

        assertEquals(3, p.periodLength)
    }

    @Test
    fun `period length is always shorter than the cycle`() {
        val p = predict(periodDays(d(2026, 3, 1), 1), today = d(2026, 3, 1), defaults = CycleDefaults(15, 14))!!
        assertEquals(14, p.periodLength)

        // Completed 15-day periods with a 15-day typical cycle: clamp to 14.
        val q = predict(periodDays(d(2026, 1, 1), 15), today = d(2026, 1, 20), defaults = CycleDefaults(15, 5))!!
        assertEquals(15, q.cycleLength)
        assertEquals(14, q.periodLength)
    }

    @Test
    fun `implausibly short cycles are ignored`() {
        // Cycles of 10 (too short to be real), 28 and 30 days.
        val days =
            periodDays(d(2026, 1, 1), 2) + periodDays(d(2026, 1, 11), 4) + periodDays(d(2026, 2, 8), 4) +
                periodDays(d(2026, 3, 10), 4)
        val p = predict(days, today = d(2026, 3, 20))!!

        assertEquals(29, p.cycleLength)
        assertEquals(2, p.cyclesUsed)
    }

    @Test
    fun `expected today is not late`() {
        val p = predict(periodDays(d(2026, 3, 1), 4), today = d(2026, 3, 31))!!
        assertEquals(0, p.daysLate)
        assertEquals(d(2026, 3, 31), p.nextStart)
    }

    @Test
    fun `a late period moves upcoming predictions to today`() {
        val p = predict(periodDays(d(2026, 3, 1), 4), today = d(2026, 4, 3))!!

        assertEquals(3, p.daysLate)
        assertEquals(d(2026, 3, 31), p.expectedNextStart)
        assertEquals(d(2026, 4, 3), p.nextStart)
    }

    @Test
    fun `upcoming periods repeat every cycle`() {
        val p = predict(periodDays(d(2026, 3, 1), 4), today = d(2026, 3, 10))!!

        assertEquals(
            listOf(
                Period(d(2026, 3, 31), d(2026, 4, 3)),
                Period(d(2026, 4, 30), d(2026, 5, 3)),
                Period(d(2026, 5, 30), d(2026, 6, 2)),
            ),
            p.upcomingPeriods(3),
        )
    }
}
