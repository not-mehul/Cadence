package app.cadence.core.domain.medication

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MedicationTest {
    @Test
    fun `only active medications with a time remind daily`() {
        assertTrue(Medication(1, "Iron", MedicationUse.DAILY, LocalTime.of(8, 0)).remindsDaily)
        assertFalse(Medication(1, "Iron", MedicationUse.DAILY).remindsDaily)
        assertFalse(Medication(1, "Iron", MedicationUse.DAILY, LocalTime.of(8, 0), active = false).remindsDaily)
        assertFalse(Medication(2, "Ibuprofen", MedicationUse.AS_NEEDED).remindsDaily)
    }

    @Test
    fun `names and reminders are validated`() {
        assertThrows<IllegalArgumentException> { Medication(1, " ", MedicationUse.DAILY) }
        assertThrows<IllegalArgumentException> { Medication(1, "x".repeat(41), MedicationUse.DAILY) }
        assertThrows<IllegalArgumentException> { Medication(1, "Ibuprofen", MedicationUse.AS_NEEDED, LocalTime.NOON) }
    }
}
