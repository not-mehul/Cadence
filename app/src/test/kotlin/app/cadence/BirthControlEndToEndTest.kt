package app.cadence

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.data.repository.ContraceptionRepository
import app.cadence.core.data.repository.MedicationRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.Goal
import app.cadence.di.FixedTimeModule
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

/**
 * Birth control through the real app: a pill schedule drives Today, doses and medications are
 * checked off, and switching to cycle tracking and back loses nothing. Today is 20 March 2026.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS-w411dp-h891dp")
class BirthControlEndToEndTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var contraception: ContraceptionRepository

    @Inject lateinit var medications: MedicationRepository

    private val today = FixedTimeModule.TODAY

    // Pack from 2 March: 20 March is pill day 19, set up on 18 March.
    private val pill =
        ContraceptiveSchedule
            .typical(
                ContraceptiveMethod.PILL,
                LocalDate.of(2026, 3, 2),
            ).copy(since = LocalDate.of(2026, 3, 18))

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            settings.update { it.copy(goal = Goal.BIRTH_CONTROL, onboardingComplete = true, contraception = pill) }
            contraception.record(LocalDate.of(2026, 3, 18), DoseStatus.TAKEN)
            medications.save(Medication(0, "Iron", MedicationUse.DAILY))
        }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT_MS) { shown(text) }

    private fun tap(text: String) {
        waitFor(text)
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable()).performClick()

    private fun setMode(label: String) {
        tab("Settings")
        tap("Mode")
        tap(label)
        tab("Today")
    }

    @Test
    fun pillScheduleDosesAndModeSwitching() {
        // Today follows the pack.
        waitFor("Break in 3 days")
        waitFor("1 dose not checked off")

        // Yesterday was taken after all; today's pill is checked off.
        tap("I did")
        tap("Take today's pill")
        tap("Done")
        waitFor("✓ Pill taken")
        compose.waitUntil(TIMEOUT_MS) { !shown("1 dose not checked off") }

        // Medications are ticked off too.
        tap("Iron")
        compose.waitUntil(TIMEOUT_MS) { runBlocking { medications.doses.first()[today] == setOf(1L) } }

        // Switch to cycle tracking: the pack goes away...
        setMode("Tracking my natural cycle")
        compose.waitUntil(TIMEOUT_MS) { !shown("Break in 3 days") }
        assertEquals(false, shown("✓ Pill taken"))

        // ...and switching back brings everything back as it was.
        setMode("Using hormonal birth control")
        waitFor("Break in 3 days")
        waitFor("✓ Pill taken")
        runBlocking {
            assertEquals(pill, settings.settings.first().contraception)
            assertEquals(
                mapOf(
                    LocalDate.of(2026, 3, 18) to DoseStatus.TAKEN,
                    LocalDate.of(2026, 3, 19) to DoseStatus.TAKEN,
                    today to DoseStatus.TAKEN,
                ),
                contraception.doses.first(),
            )
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
