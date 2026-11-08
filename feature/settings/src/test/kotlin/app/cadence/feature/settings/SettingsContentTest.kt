package app.cadence.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.security.SecuritySettings
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
    private var hidden: Set<LogCategory>? = null

    private var lockChange: Boolean? = null
    private var lockTimeout: Int? = null
    private var blockScreenshots: Boolean? = null

    private fun show(settings: UserSettings = UserSettings(onboardingComplete = true)) {
        compose.setContent {
            CadenceTheme {
                SettingsContent(
                    settings = settings,
                    actions =
                        SettingsActions(
                            onCycleLength = { cycleLength = it },
                            onPeriodLength = {},
                            onGoal = { goal = it },
                            onTheme = { theme = it },
                            onHiddenCategories = { hidden = it },
                            onDeleteAll = { deleted++ },
                            onLockChange = { lockChange = it },
                            onLockTimeout = { lockTimeout = it },
                            onBlockScreenshots = { blockScreenshots = it },
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

    @Test
    fun `categories can be hidden`() {
        show()

        compose.onNodeWithText("What to track").performScrollTo().performClick()
        compose.onNodeWithText("Sex").performScrollTo().performClick()
        compose.onNodeWithText("Stress").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(setOf(LogCategory.SEX, LogCategory.STRESS), hidden)
    }

    @Test
    fun `app lock switch asks to turn the lock on, and lock again appears only when it is on`() {
        show()

        assertEquals(0, compose.onAllNodesWithText("Lock again").fetchSemanticsNodes().size)
        compose.onNodeWithText("App lock").performScrollTo().performClick()
        assertEquals(true, lockChange)
    }

    @Test
    fun `lock timeout and screenshot blocking are adjustable`() {
        show(UserSettings(onboardingComplete = true, security = SecuritySettings(lockEnabled = true)))

        compose.onNodeWithText("App lock").performScrollTo().performClick()
        assertEquals(false, lockChange)

        compose.onNodeWithText("Lock again").performScrollTo().performClick()
        compose.onNodeWithText("After 5 minutes away").performClick()
        assertEquals(5, lockTimeout)

        compose.onNodeWithText("Block screenshots").performScrollTo().performClick()
        assertEquals(false, blockScreenshots)
    }

    @Test
    fun `privacy explainer describes how data is protected`() {
        show()

        compose.onNodeWithText("How Cadence protects your data").performScrollTo().performClick()
        compose
            .onNodeWithText(
                "Cadence doesn't have permission to use the internet",
                substring = true,
            ).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertEquals(0, compose.onAllNodesWithText("OK").fetchSemanticsNodes().size)
    }
}
