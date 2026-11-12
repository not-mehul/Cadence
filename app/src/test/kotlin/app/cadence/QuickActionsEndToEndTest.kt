package app.cadence

import android.content.Context
import android.content.pm.ShortcutManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.data.repository.ContraceptionRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.ui.quick.QuickAction
import app.cadence.di.FixedTimeModule
import app.cadence.feature.settings.AppIcon
import app.cadence.feature.settings.QuickShortcuts
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import javax.inject.Inject
import kotlin.test.assertEquals

/** Shortcuts, the Quick Settings tile and the large widget's button, through the real app. Today is 20 March 2026. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS-w411dp-h891dp")
class QuickActionsEndToEndTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var periods: PeriodRepository

    @Inject lateinit var contraception: ContraceptionRepository

    private val today = FixedTimeModule.TODAY

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking { settings.update { it.copy(onboardingComplete = true) } }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT_MS) { shown(text) }

    /** As if MainActivity had been opened with [action]'s intent. */
    private fun quick(action: QuickAction) {
        compose.waitForIdle()
        compose.runOnUiThread {
            val main = ViewModelProvider(compose.activity)[MainViewModel::class.java]
            main.onQuickAction(QuickAction.from(action.intent(compose.activity))!!)
        }
    }

    @Test
    fun periodStartedAndLogToday() {
        waitFor("Log your period to get started")
        quick(QuickAction.PERIOD_STARTED)
        waitFor("Period logged")
        compose.waitUntil(TIMEOUT_MS) { runBlocking { today in periods.flowByDay.first() } }

        // Again: nothing more to do, and it says so.
        quick(QuickAction.PERIOD_STARTED)
        waitFor("Today is already logged as a period day")

        quick(QuickAction.LOG_TODAY)
        waitFor("Changes save automatically")
    }

    @Test
    fun doseDoneWithUndo() {
        val pill = ContraceptiveSchedule.typical(ContraceptiveMethod.PILL, LocalDate.of(2026, 3, 2))
        runBlocking { settings.update { it.copy(goal = Goal.BIRTH_CONTROL, contraception = pill) } }
        waitFor("Break in 3 days")

        quick(QuickAction.DOSE_TAKEN)
        waitFor("Marked as done for today")
        compose.waitUntil(TIMEOUT_MS) { runBlocking { contraception.doses.first()[today] == DoseStatus.TAKEN } }

        // The dose card has its own "Undo"; the snackbar's is the lower one. It may still be sliding
        // in, so act on it directly rather than with a touch.
        val snackbarUndo =
            compose
                .onAllNodesWithText("Undo")
                .fetchSemanticsNodes()
                .maxBy { it.boundsInRoot.top }
                .id
        compose
            .onNode(SemanticsMatcher("snackbar Undo") { it.id == snackbarUndo })
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { runBlocking { contraception.doses.first()[today] == null } }
    }

    @Test
    fun shortcutsFollowTheSettingsAndHideWithTheDiscreetIcon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(ShortcutManager::class.java)
        val tracking = UserSettings(onboardingComplete = true)

        QuickShortcuts.publish(context, QuickShortcuts.wanted(tracking), AppIcon.STANDARD)
        assertEquals(listOf("LOG_TODAY", "PERIOD_STARTED"), manager.dynamicShortcuts.sortedBy { it.rank }.map { it.id })
        assertEquals("Period started", manager.dynamicShortcuts.single { it.id == "PERIOD_STARTED" }.shortLabel)

        val onPill =
            tracking.copy(
                goal = Goal.BIRTH_CONTROL,
                contraception = ContraceptiveSchedule.typical(ContraceptiveMethod.PILL, today),
            )
        QuickShortcuts.publish(context, QuickShortcuts.wanted(onPill), AppIcon.STANDARD)
        assertEquals(listOf("LOG_TODAY", "DOSE_TAKEN"), manager.dynamicShortcuts.sortedBy { it.rank }.map { it.id })

        QuickShortcuts.publish(context, QuickShortcuts.wanted(onPill), AppIcon.DISCREET)
        assertEquals(emptyList(), manager.dynamicShortcuts)
        assertEquals(emptyList(), QuickShortcuts.wanted(UserSettings()))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
