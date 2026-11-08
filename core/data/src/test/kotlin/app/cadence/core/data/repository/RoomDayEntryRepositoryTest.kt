package app.cadence.core.data.repository

import app.cadence.core.data.asLazy
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.log.SexActivity
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
class RoomDayEntryRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = Clock.fixed(Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC)
    private val repo = RoomDayEntryRepository(db.asLazy(), clock)
    private val periods = RoomPeriodRepository(db.asLazy(), clock)
    private val day = LocalDate.of(2026, 3, 5)

    private val full =
        DayEntry(
            date = day,
            flow = MenstrualFlow.HEAVY,
            symptoms = mapOf("cramps" to Severity.SEVERE, "headache" to Severity.MILD),
            moods = setOf("calm", "sensitive"),
            painLevel = 7,
            energy = 2,
            sleep = 4,
            stress = 3,
            sex = SexActivity.PROTECTED,
            note = "Took ibuprofen",
        )

    @After
    fun tearDown() = db.close()

    @Test
    fun `a full entry round trips`() =
        runTest {
            repo.save(full)
            assertEquals(full, repo.observe(day).first())
        }

    @Test
    fun `an unlogged day is an empty entry`() =
        runTest {
            assertEquals(DayEntry(day), repo.observe(day).first())
        }

    @Test
    fun `saving replaces symptoms and moods`() =
        runTest {
            repo.save(full)
            repo.save(full.copy(symptoms = mapOf("bloating" to Severity.MODERATE), moods = emptySet()))

            val saved = repo.observe(day).first()
            assertEquals(mapOf("bloating" to Severity.MODERATE), saved.symptoms)
            assertEquals(emptySet(), saved.moods)
        }

    @Test
    fun `saving an empty entry removes the day entirely`() =
        runTest {
            repo.save(full)
            repo.save(DayEntry(day))

            assertNull(db.dayLogDao().get(day))
            assertEquals(DayEntry(day), repo.observe(day).first())
        }

    @Test
    fun `flow saved here is a period day for predictions`() =
        runTest {
            repo.save(DayEntry(day, flow = MenstrualFlow.LIGHT))
            assertEquals(mapOf(day to MenstrualFlow.LIGHT), periods.flowByDay.first())
        }

    @Test
    fun `clearing a period day keeps the rest of that day's log`() =
        runTest {
            repo.save(DayEntry(day, flow = MenstrualFlow.MEDIUM, painLevel = 4))

            periods.clearPeriodDays(listOf(day))

            assertEquals(DayEntry(day, painLevel = 4), repo.observe(day).first())
        }

    @Test
    fun `dates with details ignore flow-only days`() =
        runTest {
            val flowOnly = day.plusDays(1)
            val moodOnly = day.plusDays(2)
            val outside = day.plusDays(40)
            repo.save(DayEntry(day, energy = 3))
            repo.save(DayEntry(flowOnly, flow = MenstrualFlow.MEDIUM))
            repo.save(DayEntry(moodOnly, moods = setOf("happy")))
            repo.save(DayEntry(outside, note = "later"))

            assertEquals(setOf(day, moodOnly), repo.observeDatesWithDetails(day, day.plusDays(30)).first())
        }
}
