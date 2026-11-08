package app.cadence.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var deleted = 0
    private var goal: Goal? = null
    private var theme: ThemePreference? = null
    private var cycleLength: Int? = null

    private fun show() {
        compose.setContent {
            CadenceTheme {
                SettingsContent(
                    settings = UserSettings(onboardingComplete = true),
                    actions =
                        SettingsActions(
                            onCycleLength = { cycleLength = it },
                            onPeriodLength = {},
                            onGoal = { goal = it },
                            onTheme = { theme = it },
                            onDeleteAll = { deleted++ },
                        ),
                )
            }
        }
    }

    @Test
    fun `delete all asks for confirmation`() {
        show()

        compose.onNodeWithText("Delete all data").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, deleted)

        compose.onNodeWithText("Delete all data").performScrollTo().performClick()
        compose.onNodeWithText("Delete everything").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun `cycle length uses a stepper`() {
        show()

        compose.onNodeWithText("Typical cycle length").performClick()
        compose
            .onNode(
                androidx.compose.ui.test
                    .hasContentDescription("Increase cycle length"),
            ).performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(29, cycleLength)
    }

    @Test
    fun `mode and theme are chosen from lists`() {
        show()

        compose.onNodeWithText("Mode").performClick()
        compose.onNodeWithText("Using hormonal birth control").performClick()
        assertEquals(Goal.BIRTH_CONTROL, goal)

        compose.onNodeWithText("Theme").performScrollTo().performClick()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(ThemePreference.DARK, theme)
    }
}
