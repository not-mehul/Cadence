package app.cadence.core.data.repository

import app.cadence.core.data.asLazy
import app.cadence.core.data.inMemoryDatabase
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RoomSettingsRepositoryTest {
    private val db = inMemoryDatabase()
    private val repo = RoomSettingsRepository(db.asLazy())

    @After
    fun tearDown() = db.close()

    @Test
    fun `defaults until saved`() =
        runTest {
            assertEquals(UserSettings(), repo.settings.first())
        }

    @Test
    fun `updates round trip every field`() =
        runTest {
            val wanted =
                UserSettings(
                    goal = Goal.BIRTH_CONTROL,
                    cycleDefaults = CycleDefaults(32, 6),
                    lutealPhaseLength = 13,
                    theme = ThemePreference.DARK,
                    onboardingComplete = true,
                    hiddenCategories = setOf(LogCategory.SEX, LogCategory.STRESS),
                )
            repo.update { wanted }

            assertEquals(wanted, repo.settings.first())
        }

    @Test
    fun `updates build on the current value`() =
        runTest {
            repo.update { it.copy(goal = Goal.BIRTH_CONTROL) }
            repo.update { it.copy(theme = ThemePreference.LIGHT) }

            val s = repo.settings.first()
            assertEquals(Goal.BIRTH_CONTROL, s.goal)
            assertEquals(ThemePreference.LIGHT, s.theme)
        }
}
