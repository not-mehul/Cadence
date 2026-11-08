package app.cadence.core.data.repository

import app.cadence.core.data.asLazy
import app.cadence.core.data.db.SettingsEntity
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ContraceptionSettingsTest {
    private val db = inMemoryDatabase()
    private val settings = RoomSettingsRepository(db.asLazy())
    private val patch =
        ContraceptiveSchedule(
            ContraceptiveMethod.PATCH,
            LocalDate.of(2026, 3, 2),
            activeDays = 21,
            breakDays = 7,
            since = LocalDate.of(2026, 3, 5),
            reminderEnabled = true,
            reminderTime = LocalTime.of(7, 15),
        )

    @After
    fun tearDown() = db.close()

    @Test
    fun `switching modes keeps the schedule`() =
        runTest {
            settings.update { it.copy(goal = Goal.BIRTH_CONTROL, contraception = patch) }
            assertEquals(patch, settings.settings.first().activeSchedule)

            settings.update { it.copy(goal = Goal.TRACK) }
            assertNull(settings.settings.first().activeSchedule)
            assertEquals(patch, settings.settings.first().contraception)

            settings.update { it.copy(goal = Goal.BIRTH_CONTROL) }
            assertEquals(patch, settings.settings.first().activeSchedule)
        }

    @Test
    fun `no schedule, and a stored schedule that is no longer valid, read as not set up`() {
        assertNull(SettingsEntity.from(UserSettings()).toModel().contraception)
        val stored = SettingsEntity.from(UserSettings(contraception = patch))
        // Patches must be whole weeks; a damaged row can't produce a schedule.
        assertNull(stored.copy(bcActiveDays = 20).toModel().contraception)
        assertNull(stored.copy(bcPackStart = null).toModel().contraception)
        // Older rows have no "since": the pack start stands in.
        assertEquals(
            patch.packStart,
            stored
                .copy(bcSince = null)
                .toModel()
                .contraception
                ?.since,
        )
    }
}
