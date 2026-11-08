package app.cadence.core.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalTest {
    @Test
    fun `only the two decided modes exist`() {
        assertEquals(listOf(Goal.TRACK, Goal.BIRTH_CONTROL), Goal.entries)
    }

    @Test
    fun `ovulation estimates are shown when tracking`() {
        assertTrue(Goal.TRACK.showsOvulationEstimates)
    }

    @Test
    fun `ovulation estimates are hidden on hormonal birth control`() {
        assertFalse(Goal.BIRTH_CONTROL.showsOvulationEstimates)
    }
}
