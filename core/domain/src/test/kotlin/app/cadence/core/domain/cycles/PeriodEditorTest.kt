package app.cadence.core.domain.cycles

import app.cadence.core.domain.d
import app.cadence.core.domain.model.Period
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PeriodEditorTest {
    @Test
    fun `starting a period logs the expected number of days`() {
        assertEquals(
            listOf(d(2026, 3, 30), d(2026, 3, 31), d(2026, 4, 1), d(2026, 4, 2)),
            PeriodEditor.daysForStart(d(2026, 3, 30), expectedLength = 4),
        )
    }

    @Test
    fun `expected length is clamped to a plausible period`() {
        assertEquals(1, PeriodEditor.daysForStart(d(2026, 3, 1), expectedLength = 0).size)
        assertEquals(15, PeriodEditor.daysForStart(d(2026, 3, 1), expectedLength = 40).size)
    }

    @Test
    fun `ending a period clears the days after the last day`() {
        val period = Period(d(2026, 3, 1), d(2026, 3, 5))

        assertEquals(listOf(d(2026, 3, 4), d(2026, 3, 5)), PeriodEditor.daysToClearForEnd(period, d(2026, 3, 3)))
        assertEquals(emptyList(), PeriodEditor.daysToClearForEnd(period, d(2026, 3, 5)))
    }

    @Test
    fun `the last day must be inside the period`() {
        val period = Period(d(2026, 3, 1), d(2026, 3, 5))
        assertThrows<IllegalArgumentException> { PeriodEditor.daysToClearForEnd(period, d(2026, 3, 6)) }
    }
}
