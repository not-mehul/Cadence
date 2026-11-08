package app.cadence

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Phase 1 journey through the real app (view models, repositories, Room), with an
 * in-memory database and today pinned to 20 March 2026: onboard, log a period, undo,
 * see it on the calendar, then erase everything.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS-w411dp-h891dp")
class CadenceEndToEndTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun tap(text: String) {
        waitFor(text)
        val node = compose.onNodeWithText(text)
        // Scroll into view when the node is inside a scrolling container (fixed buttons aren't).
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable()).performClick()

    @Test
    fun onboardLogAPeriodAndEraseEverything() {
        // Onboarding, skipping the date and accepting typical lengths.
        tap("Get started")
        tap("I don't remember")
        waitFor("How long is your cycle, usually?")
        tap("Next")
        waitFor("How many days does your period usually last?")
        tap("Next")
        tap("No, track my natural cycle")
        tap("Finish")

        // Today: nothing logged yet.
        waitFor("Log your period to get started")
        tap("Period started")
        waitFor("Day 1 of your period")

        // Undo from the snackbar, then log again.
        tap("Undo")
        waitFor("Log your period to get started")
        tap("Period started")
        waitFor("Day 1 of your period")

        // Calendar shows today as a logged period day.
        tab("Calendar")
        val todayCell =
            hasContentDescription("March 20, 2026, today") and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Period day")
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(todayCell).fetchSemanticsNodes().isNotEmpty() }

        // Settings: delete everything returns to onboarding.
        tab("Settings")
        tap("Delete all data")
        tap("Delete everything")
        waitFor("Welcome to Cadence")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
