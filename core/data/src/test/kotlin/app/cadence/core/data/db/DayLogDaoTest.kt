package app.cadence.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.domain.model.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DayLogDaoTest {
    private lateinit var db: CadenceDatabase
    private lateinit var dao: DayLogDao
    private val now = Instant.parse("2026-11-08T10:00:00Z")

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CadenceDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.dayLogDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `upsert inserts then replaces the same day`() =
        runTest {
            val day = LocalDate.of(2026, 11, 1)
            dao.upsert(DayLogEntity(day, Flow.MEDIUM, updatedAt = now))
            dao.upsert(DayLogEntity(day, Flow.HEAVY, note = "cramps", updatedAt = now))

            assertEquals(DayLogEntity(day, Flow.HEAVY, "cramps", now), dao.get(day))
            assertEquals(1, dao.observeAll().first().size)
        }

    @Test
    fun `range query is inclusive and ordered by date`() =
        runTest {
            listOf(5, 1, 3, 9).forEach { d ->
                dao.upsert(DayLogEntity(LocalDate.of(2026, 11, d), Flow.LIGHT, updatedAt = now))
            }

            val days = dao.observeRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)).first().map { it.date }

            assertEquals(listOf(1, 3, 5).map { LocalDate.of(2026, 11, it) }, days)
        }

    @Test
    fun `dates across a DST change and leap day are stored exactly`() =
        runTest {
            val days = listOf(LocalDate.of(2028, 2, 29), LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))
            days.forEach { dao.upsert(DayLogEntity(it, Flow.SPOTTING, updatedAt = now)) }

            assertEquals(days.sorted(), dao.observeAll().first().map { it.date })
        }

    @Test
    fun `delete removes one day and deleteAll removes everything`() =
        runTest {
            val a = LocalDate.of(2026, 11, 1)
            val b = LocalDate.of(2026, 11, 2)
            dao.upsert(DayLogEntity(a, Flow.LIGHT, updatedAt = now))
            dao.upsert(DayLogEntity(b, Flow.LIGHT, updatedAt = now))

            dao.delete(a)
            assertNull(dao.get(a))
            assertEquals(1, dao.observeAll().first().size)

            dao.deleteAll()
            assertEquals(emptyList(), dao.observeAll().first())
        }
}
