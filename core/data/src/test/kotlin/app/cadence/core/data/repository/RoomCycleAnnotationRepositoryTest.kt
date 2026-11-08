package app.cadence.core.data.repository

import app.cadence.core.data.asLazy
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.domain.log.DayEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RoomCycleAnnotationRepositoryTest {
    private val db = inMemoryDatabase()
    private val repo = RoomCycleAnnotationRepository(db.asLazy())

    @After
    fun tearDown() = db.close()

    @Test
    fun `excluding and including a cycle`() =
        runTest {
            val a = LocalDate.of(2026, 1, 1)
            val b = LocalDate.of(2026, 1, 29)

            repo.setExcluded(a, true)
            repo.setExcluded(b, true)
            assertEquals(setOf(a, b), repo.excludedStarts.first())

            repo.setExcluded(a, false)
            assertEquals(setOf(b), repo.excludedStarts.first())

            repo.setExcluded(a, false)
            assertEquals(setOf(b), repo.excludedStarts.first(), "including twice is harmless")
        }

    @Test
    fun `pain by day lists only days with pain logged`() =
        runTest {
            val entries = RoomDayEntryRepository(db.asLazy(), Clock.systemUTC())
            val day = LocalDate.of(2026, 3, 2)
            entries.save(DayEntry(day, painLevel = 8))
            entries.save(DayEntry(day.plusDays(1), energy = 3))

            assertEquals(mapOf(day to 8), entries.painByDay.first())
        }
}
