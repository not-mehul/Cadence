package app.cadence.core.domain.cycles

import app.cadence.core.domain.d
import app.cadence.core.domain.model.Cycle
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.MenstrualFlow.HEAVY
import app.cadence.core.domain.model.MenstrualFlow.LIGHT
import app.cadence.core.domain.model.MenstrualFlow.MEDIUM
import app.cadence.core.domain.model.MenstrualFlow.SPOTTING
import app.cadence.core.domain.model.Period
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CycleHistoryTest {
    private fun periodsOf(vararg days: Pair<java.time.LocalDate, MenstrualFlow>) =
        CycleHistory.from(days.toMap()).periods

    @Test
    fun `no logs means no periods or cycles`() {
        assertSame(CycleHistory.EMPTY, CycleHistory.from(emptyMap()))
        assertEquals(emptyList(), CycleHistory.EMPTY.cycles)
    }

    @Test
    fun `a single day is a one-day period`() {
        assertEquals(listOf(Period(d(2026, 3, 1), d(2026, 3, 1))), periodsOf(d(2026, 3, 1) to MEDIUM))
    }

    @Test
    fun `consecutive days of any flow form one period`() {
        val periods = periodsOf(d(2026, 3, 1) to HEAVY, d(2026, 3, 2) to MEDIUM, d(2026, 3, 3) to LIGHT)
        assertEquals(listOf(Period(d(2026, 3, 1), d(2026, 3, 3))), periods)
    }

    @Test
    fun `a one-day gap is bridged`() {
        val periods = periodsOf(d(2026, 3, 1) to MEDIUM, d(2026, 3, 3) to LIGHT)
        assertEquals(listOf(Period(d(2026, 3, 1), d(2026, 3, 3))), periods)
    }

    @Test
    fun `a two-day gap starts a new period`() {
        val periods = periodsOf(d(2026, 3, 1) to MEDIUM, d(2026, 3, 4) to MEDIUM)
        assertEquals(listOf(Period(d(2026, 3, 1), d(2026, 3, 1)), Period(d(2026, 3, 4), d(2026, 3, 4))), periods)
    }

    @Test
    fun `spotting never starts or lengthens a period`() {
        val periods =
            periodsOf(
                d(2026, 2, 27) to SPOTTING,
                d(2026, 3, 1) to MEDIUM,
                d(2026, 3, 2) to LIGHT,
                d(2026, 3, 3) to SPOTTING,
                d(2026, 3, 4) to SPOTTING,
            )
        assertEquals(listOf(Period(d(2026, 3, 1), d(2026, 3, 2))), periods)
    }

    @Test
    fun `spotting only means no periods`() {
        assertEquals(emptyList(), periodsOf(d(2026, 3, 1) to SPOTTING, d(2026, 3, 2) to SPOTTING))
    }

    @Test
    fun `cycles run from each period start to the next`() {
        val history =
            CycleHistory.from(
                periodDays(d(2026, 1, 1), 5) + periodDays(d(2026, 1, 29), 4) + periodDays(d(2026, 3, 1), 5),
            )
        assertEquals(listOf(Cycle(d(2026, 1, 1), d(2026, 1, 29)), Cycle(d(2026, 1, 29), d(2026, 3, 1))), history.cycles)
        assertEquals(listOf(28, 31), history.cycles.map { it.length })
    }

    @Test
    fun `latest period started by a date ignores later periods`() {
        val history = CycleHistory.from(periodDays(d(2026, 1, 1), 5) + periodDays(d(2026, 1, 29), 5))
        assertEquals(d(2026, 1, 1), history.latestPeriodStartedBy(d(2026, 1, 28))?.start)
        assertEquals(d(2026, 1, 29), history.latestPeriodStartedBy(d(2026, 1, 29))?.start)
        assertNull(history.latestPeriodStartedBy(d(2025, 12, 31)))
    }

    @Test
    fun `is period day`() {
        val history = CycleHistory.from(periodDays(d(2026, 1, 1), 3))
        assertTrue(history.isPeriodDay(d(2026, 1, 3)))
        assertFalse(history.isPeriodDay(d(2026, 1, 4)))
    }
}
