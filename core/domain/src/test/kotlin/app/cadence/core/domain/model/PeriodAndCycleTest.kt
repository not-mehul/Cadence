package app.cadence.core.domain.model

import app.cadence.core.domain.d
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeriodAndCycleTest {
    @Test
    fun `period length counts both ends`() {
        assertEquals(1, Period(d(2026, 3, 1), d(2026, 3, 1)).length)
        assertEquals(5, Period(d(2026, 3, 1), d(2026, 3, 5)).length)
    }

    @Test
    fun `period across a month boundary and leap day`() {
        val p = Period(d(2028, 2, 27), d(2028, 3, 2))
        assertEquals(5, p.length)
        assertEquals(listOf(d(2028, 2, 27), d(2028, 2, 28), d(2028, 2, 29), d(2028, 3, 1), d(2028, 3, 2)), p.days())
    }

    @Test
    fun `contains is inclusive`() {
        val p = Period(d(2026, 3, 1), d(2026, 3, 5))
        assertTrue(d(2026, 3, 1) in p)
        assertTrue(d(2026, 3, 5) in p)
        assertFalse(d(2026, 2, 28) in p)
        assertFalse(d(2026, 3, 6) in p)
    }

    @Test
    fun `period cannot end before it starts`() {
        assertThrows<IllegalArgumentException> { Period(d(2026, 3, 5), d(2026, 3, 1)) }
    }

    @Test
    fun `cycle length is days between starts`() {
        assertEquals(28, Cycle(d(2026, 3, 1), d(2026, 3, 29)).length)
        // Across the DST change in Europe (29 March 2026): calendar days, not hours.
        assertEquals(28, Cycle(d(2026, 3, 15), d(2026, 4, 12)).length)
    }

    @Test
    fun `cycle must end after it starts`() {
        assertThrows<IllegalArgumentException> { Cycle(d(2026, 3, 1), d(2026, 3, 1)) }
    }

    @Test
    fun `settings defaults`() {
        val s = UserSettings()
        assertEquals(Goal.TRACK, s.goal)
        assertEquals(CycleDefaults(), s.cycleDefaults)
        assertEquals(14, s.lutealPhaseLength)
        assertEquals(ThemePreference.SYSTEM, s.theme)
        assertFalse(s.onboardingComplete)
    }
}
