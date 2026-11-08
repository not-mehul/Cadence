package app.cadence.core.data.repository

import app.cadence.core.data.asLazy
import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.DayLogEntity
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.domain.model.MenstrualFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class RoomPeriodRepositoryTest {
    private val db: CadenceDatabase = inMemoryDatabase()
    private val clock = Clock.fixed(Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC)
    private val repo = RoomPeriodRepository(db.asLazy(), clock)

    private fun day(d: Int) = LocalDate.of(2026, 3, d)

    @After
    fun tearDown() = db.close()

    @Test
    fun `logging days adds medium flow and reports what was added`() =
        runTest {
            val added = repo.logPeriodDays(listOf(day(3), day(1), day(2)))

            assertEquals(listOf(day(1), day(2), day(3)), added)
            assertEquals((1..3).associate { day(it) to MenstrualFlow.MEDIUM }, repo.flowByDay.first())
        }

    @Test
    fun `logging keeps existing flow and does not report it as added`() =
        runTest {
            db.dayLogDao().upsert(DayLogEntity(day(1), MenstrualFlow.HEAVY, updatedAt = Instant.EPOCH))

            val added = repo.logPeriodDays(listOf(day(1), day(2)))

            assertEquals(listOf(day(2)), added)
            assertEquals(MenstrualFlow.HEAVY, repo.flowByDay.first()[day(1)])
        }

    @Test
    fun `logging a day that only has a note keeps the note`() =
        runTest {
            db.dayLogDao().upsert(DayLogEntity(day(4), flow = null, note = "note", updatedAt = Instant.EPOCH))

            assertEquals(listOf(day(4)), repo.logPeriodDays(listOf(day(4))))
            assertEquals(
                DayLogEntity(day(4), MenstrualFlow.MEDIUM, "note", updatedAt = Instant.now(clock)),
                db.dayLogDao().get(day(4)),
            )
        }

    @Test
    fun `clearing removes empty rows but keeps notes`() =
        runTest {
            repo.logPeriodDays(listOf(day(1), day(2)))
            db.dayLogDao().upsert(DayLogEntity(day(2), MenstrualFlow.MEDIUM, note = "note", updatedAt = Instant.EPOCH))

            repo.clearPeriodDays(listOf(day(1), day(2)))

            assertNull(db.dayLogDao().get(day(1)), "an empty day leaves no row behind")
            assertEquals(DayLogEntity(day(2), null, "note", updatedAt = Instant.now(clock)), db.dayLogDao().get(day(2)))
            assertEquals(emptyMap(), repo.flowByDay.first())
        }

    @Test
    fun `clearing nothing is a no-op`() =
        runTest {
            repo.logPeriodDays(listOf(day(1)))
            repo.clearPeriodDays(emptyList())
            assertEquals(1, repo.flowByDay.first().size)
        }
}
