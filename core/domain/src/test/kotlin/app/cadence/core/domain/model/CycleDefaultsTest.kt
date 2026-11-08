package app.cadence.core.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class CycleDefaultsTest {
    @Test
    fun `defaults are a 28 day cycle with a 5 day period`() {
        val defaults = CycleDefaults()
        assertEquals(28, defaults.cycleLength)
        assertEquals(5, defaults.periodLength)
    }

    @Test
    fun `allowed ranges match the plan`() {
        // Onboarding pickers use these bounds; cycles outside 15..60 days are treated as logging gaps.
        assertEquals(15..60, CycleDefaults.CYCLE_LENGTH_RANGE)
        assertEquals(1..15, CycleDefaults.PERIOD_LENGTH_RANGE)
    }

    @ParameterizedTest(name = "cycle {0}, period {1} is accepted")
    @CsvSource("15, 1", "60, 15", "21, 7", "35, 4")
    fun `values at and inside the bounds are accepted`(
        cycle: Int,
        period: Int,
    ) {
        val defaults = CycleDefaults(cycle, period)
        assertEquals(cycle, defaults.cycleLength)
        assertEquals(period, defaults.periodLength)
    }

    @ParameterizedTest(name = "cycle {0}, period {1} is rejected")
    @CsvSource(
        "14, 5", // cycle too short
        "61, 5", // cycle too long
        "28, 0", // period too short
        "28, 16", // period too long
        "15, 15", // period not shorter than cycle
    )
    fun `values outside the bounds are rejected`(
        cycle: Int,
        period: Int,
    ) {
        assertThrows<IllegalArgumentException> { CycleDefaults(cycle, period) }
    }
}
