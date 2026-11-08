package app.cadence.core.data.backup

import app.cadence.core.data.asLazy
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.data.repository.RoomCycleAnnotationRepository
import app.cadence.core.data.repository.RoomDataEraser
import app.cadence.core.data.repository.RoomDayEntryRepository
import app.cadence.core.data.repository.RoomSettingsRepository
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
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
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RoomBackupRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = Clock.systemUTC()
    private val backups = RoomBackupRepository(db.asLazy(), clock)
    private val entries = RoomDayEntryRepository(db.asLazy(), clock)
    private val settings = RoomSettingsRepository(db.asLazy())
    private val annotations = RoomCycleAnnotationRepository(db.asLazy())
    private val d1 = LocalDate.of(2026, 3, 1)

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        settings.update { it.copy(goal = Goal.BIRTH_CONTROL, onboardingComplete = true) }
        entries.save(
            DayEntry(d1, flow = MenstrualFlow.HEAVY, symptoms = mapOf("cramps" to Severity.MODERATE), painLevel = 6),
        )
        entries.save(DayEntry(d1.plusDays(1), moods = setOf("calm"), note = "fine"))
        annotations.setExcluded(LocalDate.of(2026, 1, 1), true)
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
            assertEquals(backup.entries.first(), after.entries.first { it.date == d1 })
            assertEquals(DayEntry(d1.plusDays(20), stress = 2), after.entries.last())
            assertEquals(3, after.entries.size)
            assertEquals(setOf(LocalDate.of(2026, 1, 1)), after.excludedCycleStarts)
        }
}
