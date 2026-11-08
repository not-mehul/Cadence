package app.cadence.core.data.repository

import app.cadence.core.data.asLazy
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.UserSettings
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
class RoomDataEraserTest {
    private val db = inMemoryDatabase()

    @After
    fun tearDown() = db.close()

    @Test
    fun `erase removes every period and resets settings`() =
        runTest {
            val periods = RoomPeriodRepository(db.asLazy(), Clock.systemUTC())
            val settings = RoomSettingsRepository(db.asLazy())
            periods.logPeriodDays(listOf(LocalDate.of(2026, 3, 1)))
            val entries = RoomDayEntryRepository(db.asLazy(), Clock.systemUTC())
            entries.save(DayEntry(LocalDate.of(2026, 3, 2), moods = setOf("calm")))
            settings.update { it.copy(onboardingComplete = true) }

            RoomDataEraser(db.asLazy()).eraseAll()

            assertEquals(emptyMap(), periods.flowByDay.first())
            assertEquals(UserSettings(), settings.settings.first(), "back to onboarding")
            assertEquals(DayEntry(LocalDate.of(2026, 3, 2)), entries.observe(LocalDate.of(2026, 3, 2)).first())
        }
}
