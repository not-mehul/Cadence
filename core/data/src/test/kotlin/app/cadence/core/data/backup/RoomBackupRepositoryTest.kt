package app.cadence.core.data.backup

import app.cadence.core.data.asLazy
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.data.repository.RoomContraceptionRepository
import app.cadence.core.data.repository.RoomCustomTagRepository
import app.cadence.core.data.repository.RoomCycleAnnotationRepository
import app.cadence.core.data.repository.RoomDataEraser
import app.cadence.core.data.repository.RoomDayEntryRepository
import app.cadence.core.data.repository.RoomMedicationRepository
import app.cadence.core.data.repository.RoomSettingsRepository
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.security.SecuritySettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RoomBackupRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = Clock.systemUTC()
    private val backups = RoomBackupRepository(db.asLazy(), clock)
    private val entries = RoomDayEntryRepository(db.asLazy(), clock)
    private val settings = RoomSettingsRepository(db.asLazy())
    private val annotations = RoomCycleAnnotationRepository(db.asLazy())
    private val contraception = RoomContraceptionRepository(db.asLazy(), clock)
    private val medications = RoomMedicationRepository(db.asLazy())
    private val tags = RoomCustomTagRepository(db.asLazy())
    private val d1 = LocalDate.of(2026, 3, 1)

    @After
    fun tearDown() = db.close()

    private val schedule =
        ContraceptiveSchedule(
            ContraceptiveMethod.PILL,
            LocalDate.of(2026, 2, 23),
            activeDays = 24,
            breakDays = 4,
            placebo = true,
            since = LocalDate.of(2026, 2, 25),
            reminderEnabled = true,
            reminderTime = LocalTime.of(21, 30),
        )

    private suspend fun seed() {
        settings.update { it.copy(goal = Goal.BIRTH_CONTROL, onboardingComplete = true, contraception = schedule) }
        val travel = tags.create("Travel")
        tags.create("Gym")
        entries.save(
            DayEntry(
                d1,
                flow = MenstrualFlow.HEAVY,
                symptoms = mapOf("cramps" to Severity.MODERATE),
                painLevel = 6,
                tags = setOf(travel),
            ),
        )
        entries.save(DayEntry(d1.plusDays(1), moods = setOf("calm"), note = "fine"))
        annotations.setExcluded(LocalDate.of(2026, 1, 1), true)
        contraception.record(d1, DoseStatus.TAKEN)
        contraception.record(d1.plusDays(1), DoseStatus.MISSED)
        val iron = medications.save(Medication(0, "Iron", MedicationUse.DAILY, LocalTime.of(8, 0)))
        val ibuprofen = medications.save(Medication(0, "Ibuprofen", MedicationUse.AS_NEEDED, active = false))
        medications.setTaken(d1, iron, true)
        medications.setTaken(d1, ibuprofen, true)
        medications.setTaken(d1.plusDays(1), iron, true)
    }

    @Test
    fun `export, delete everything, import gives identical data`() =
        runTest {
            seed()
            val before = backups.snapshot()
            val file = BackupCodec.encode(before, Instant.now(), "pw".toCharArray(), iterations = 1_000)

            RoomDataEraser(db.asLazy()).eraseAll()
            assertEquals(emptyList(), backups.snapshot().entries)

            backups.restore(BackupCodec.decode(file, "pw".toCharArray()), ImportMode.REPLACE)

            assertEquals(before, backups.snapshot())
            // Everything Phase 6 adds made the trip.
            assertEquals(schedule, before.settings.contraception)
            assertEquals(2, before.customTags.size)
            assertEquals(2, before.medications.size)
            assertEquals(2, before.contraceptiveDoses.size)
            assertEquals(3, before.medicationDoses.values.sumOf { it.size })
        }

    @Test
    fun `merge matches tags and medications by name and adds the rest`() =
        runTest {
            seed()
            val backup = backups.snapshot()
            RoomDataEraser(db.asLazy()).eraseAll()
            // Here already: "travel" (different id and case) and a different medication.
            tags.create("Other")
            val localTravel = tags.create("travel")
            medications.save(Medication(0, "Vitamin D", MedicationUse.DAILY))
            val localIron = medications.save(Medication(0, "IRON", MedicationUse.DAILY))

            backups.restore(backup, ImportMode.MERGE)

            val after = backups.snapshot()
            assertEquals(listOf("Other", "travel", "Gym"), after.customTags.map { it.label })
            assertEquals(setOf(localTravel), after.entries.first { it.date == d1 }.tags)
            assertEquals(listOf("Vitamin D", "IRON", "Ibuprofen"), after.medications.map { it.name })
            assertEquals(setOf(localIron), after.medicationDoses[d1.plusDays(1)])
            assertEquals(backup.contraceptiveDoses, after.contraceptiveDoses)
        }

    @Test
    fun `replace removes tags, medications and doses that aren't in the backup`() =
        runTest {
            seed()
            val backup = backups.snapshot()
            tags.create("Extra")
            val extra = medications.save(Medication(0, "Extra", MedicationUse.AS_NEEDED))
            medications.setTaken(d1.plusDays(5), extra, true)
            contraception.record(d1.plusDays(5), DoseStatus.TAKEN)

            backups.restore(backup, ImportMode.REPLACE)

            assertEquals(backup, backups.snapshot())
        }

    @Test
    fun `replace keeps this phone's security settings`() =
        runTest {
            seed()
            val backup = backups.snapshot()
            settings.update { it.copy(security = SecuritySettings(lockEnabled = true, lockTimeoutMinutes = 5)) }

            backups.restore(backup, ImportMode.REPLACE)

            assertEquals(
                SecuritySettings(lockEnabled = true, lockTimeoutMinutes = 5),
                settings.settings.first().security,
            )
            assertEquals(Goal.BIRTH_CONTROL, settings.settings.first().goal)
        }

    @Test
    fun `replace removes days that aren't in the backup`() =
        runTest {
            seed()
            val backup = backups.snapshot()
            entries.save(DayEntry(d1.plusDays(10), energy = 3))

            backups.restore(backup, ImportMode.REPLACE)

            assertEquals(backup.entries, backups.snapshot().entries)
        }

    @Test
    fun `merge keeps local days and settings, and the backup wins on the same day`() =
        runTest {
            seed()
            val backup = backups.snapshot()
            RoomDataEraser(db.asLazy()).eraseAll()
            settings.update { it.copy(goal = Goal.TRACK, onboardingComplete = true) }
            entries.save(DayEntry(d1, energy = 1))
            entries.save(DayEntry(d1.plusDays(20), stress = 2))

            backups.restore(backup, ImportMode.MERGE)

            val after = backups.snapshot()
            assertEquals(Goal.TRACK, after.settings.goal)
            // Merged tags get ids of their own here, so compare them by label.
            val merged = after.entries.first { it.date == d1 }
            assertEquals(backup.entries.first().copy(tags = emptySet()), merged.copy(tags = emptySet()))
            assertEquals(listOf("Travel"), merged.tags.map { id -> after.customTags.first { it.id == id }.label })
            assertEquals(DayEntry(d1.plusDays(20), stress = 2), after.entries.last())
            assertEquals(3, after.entries.size)
            assertEquals(setOf(LocalDate.of(2026, 1, 1)), after.excludedCycleStarts)
        }
}
