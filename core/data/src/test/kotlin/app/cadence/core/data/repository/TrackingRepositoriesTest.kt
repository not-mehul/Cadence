package app.cadence.core.data.repository

import app.cadence.core.data.asLazy
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class TrackingRepositoriesTest {
    private val db = inMemoryDatabase()
    private val clock = Clock.systemUTC()
    private val contraception = RoomContraceptionRepository(db.asLazy(), clock)
    private val medications = RoomMedicationRepository(db.asLazy())
    private val tags = RoomCustomTagRepository(db.asLazy())
    private val entries = RoomDayEntryRepository(db.asLazy(), clock)
    private val d1 = LocalDate.of(2026, 3, 1)

    @After
    fun tearDown() = db.close()

    @Test
    fun `doses are recorded, changed and cleared`() =
        runTest {
            contraception.record(d1, DoseStatus.TAKEN)
            contraception.record(d1.plusDays(1), DoseStatus.MISSED)
            contraception.record(d1.plusDays(1), DoseStatus.TAKEN)
            assertEquals(mapOf(d1 to DoseStatus.TAKEN, d1.plusDays(1) to DoseStatus.TAKEN), contraception.doses.first())

            contraception.record(d1, null)
            assertEquals(setOf(d1.plusDays(1)), contraception.doses.first().keys)
        }

    @Test
    fun `medications are added, edited, taken and deleted with their doses`() =
        runTest {
            val iron = medications.save(Medication(0, " Iron ", MedicationUse.DAILY, LocalTime.of(8, 30)))
            val ibuprofen = medications.save(Medication(0, "ibuprofen", MedicationUse.AS_NEEDED))
            assertEquals(
                listOf(
                    Medication(ibuprofen, "ibuprofen", MedicationUse.AS_NEEDED),
                    Medication(iron, "Iron", MedicationUse.DAILY, LocalTime.of(8, 30)),
                ),
                medications.medications.first(),
            )

            medications.save(Medication(iron, "Iron", MedicationUse.DAILY, null, active = false))
            assertEquals(
                false,
                medications.medications
                    .first()
                    .first { it.id == iron }
                    .active,
            )

            medications.setTaken(d1, iron, true)
            medications.setTaken(d1, iron, true)
            medications.setTaken(d1, ibuprofen, true)
            medications.setTaken(d1.plusDays(1), iron, true)
            medications.setTaken(d1.plusDays(1), iron, false)
            assertEquals(mapOf(d1 to setOf(iron, ibuprofen)), medications.doses.first())

            medications.delete(iron)
            assertEquals(listOf(ibuprofen), medications.medications.first().map { it.id })
            assertEquals(mapOf(d1 to setOf(ibuprofen)), medications.doses.first())
        }

    @Test
    fun `tags are created once per label, renamed, and deleted from every day`() =
        runTest {
            val travel = tags.create(" Travel ")
            assertEquals(travel, tags.create("travel"))
            val gym = tags.create("Gym")
            assertEquals(listOf(CustomTag(gym, "Gym"), CustomTag(travel, "Travel")), tags.tags.first())
            assertFailsWith<IllegalArgumentException> { tags.create("  ") }

            tags.rename(gym, "Climbing")
            assertEquals(listOf("Climbing", "Travel"), tags.tags.first().map { it.label })

            entries.save(DayEntry(d1, tags = setOf(travel, gym), moods = setOf("calm")))
            assertEquals(setOf(travel, gym), entries.observe(d1).first().tags)
            assertEquals(mapOf(d1 to setOf("calm", "tag:$travel", "tag:$gym")), entries.patternItemsByDay.first())

            tags.delete(travel)
            assertEquals(DayEntry(d1, tags = setOf(gym), moods = setOf("calm")), entries.observe(d1).first())
            assertEquals(listOf("Climbing"), tags.tags.first().map { it.label })
        }
}
