package app.cadence.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB-w411dp-h1600dp")
class TodayBirthControlTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 20)

    // Pack from 2 March: today is pill day 19, the break is 23–29 March.
    private val pill =
        ContraceptiveSchedule
            .typical(
                ContraceptiveMethod.PILL,
                LocalDate.of(2026, 3, 2),
            ).copy(since = LocalDate.of(2026, 3, 15))
    private val recorded = mutableListOf<Pair<LocalDate, DoseStatus?>>()
    private val taken = mutableListOf<Pair<Long, Boolean>>()
    private var openedSettings = false

    private fun show(
        schedule: ContraceptiveSchedule? = pill,
        doses: Map<LocalDate, DoseStatus> = emptyMap(),
        day: LocalDate = today,
        medications: List<MedicationToday> = emptyList(),
    ) {
        val settings = UserSettings(goal = Goal.BIRTH_CONTROL, onboardingComplete = true, contraception = schedule)
        val state =
            TodayUiState.Ready(
                snapshot = CycleSnapshot.compute(emptyMap(), settings, day),
                goal = Goal.BIRTH_CONTROL,
                typicalPeriodLength = 5,
                todayEntry = DayEntry(day),
                birthControl = schedule?.let { TodayViewModel.birthControlToday(it, doses, day) },
                medications = medications,
            )
        compose.setContent {
            CadenceTheme {
                TodayContent(
                    state,
                    onStartPeriod = {},
                    onEndPeriod = {},
                    onLogToday = {},
                    onOpenLog = {},
                    onRecordDose = { date, status -> recorded += date to status },
                    onMedicationTakenChange = { id, on -> taken += id to on },
                    onOpenSettings = { openedSettings = true },
                )
            }
        }
    }

    @Test
    fun `the pack countdown and today's pill, checked off with one tap`() {
        show(doses = (15..19).associate { LocalDate.of(2026, 3, it) to DoseStatus.TAKEN })

        compose.onNodeWithText("Break in 3 days").assertIsDisplayed()
        compose.onNodeWithText("Next break 23 Mar to 29 Mar, when a bleed is likely").assertIsDisplayed()
        compose.onNodeWithText("Take today's pill").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").performScrollTo().performClick()

        assertEquals(listOf<Pair<LocalDate, DoseStatus?>>(today to DoseStatus.TAKEN), recorded)
        assertEquals(0, compose.onAllNodesWithText("not checked off", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a checked-off dose can be undone, and the next one is shown`() {
        show(doses = (15..20).associate { LocalDate.of(2026, 3, it) to DoseStatus.TAKEN })

        compose.onNodeWithText("✓ Pill taken").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        assertEquals(listOf<Pair<LocalDate, DoseStatus?>>(today to null), recorded)
    }

    @Test
    fun `missed doses are asked about, with advice to check the leaflet`() {
        show(
            doses = mapOf(LocalDate.of(2026, 3, 15) to DoseStatus.TAKEN, LocalDate.of(2026, 3, 17) to DoseStatus.TAKEN),
        )

        compose.onNodeWithText("3 doses not checked off").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("check your pack's leaflet", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("I did")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("I missed it")[1].performScrollTo().performClick()

        assertEquals(
            listOf<Pair<LocalDate, DoseStatus?>>(
                LocalDate.of(2026, 3, 16) to DoseStatus.TAKEN,
                LocalDate.of(2026, 3, 18) to DoseStatus.MISSED,
            ),
            recorded,
        )
    }

    @Test
    fun `during the break there's nothing to take`() {
        show(
            day = LocalDate.of(2026, 3, 25),
            doses = (15..22).associate { LocalDate.of(2026, 3, it) to DoseStatus.TAKEN },
        )

        compose.onNodeWithText("Break, day 3 of 7").assertIsDisplayed()
        compose
            .onNodeWithText(
                "A withdrawal bleed usually comes during the break",
                substring = true,
            ).assertIsDisplayed()
        compose.onNodeWithText("Nothing to do today.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `without a schedule, birth control mode points to settings`() {
        show(schedule = null)

        compose.onNodeWithText("Using a pill, patch or ring?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Set up in Settings").performScrollTo().performClick()
        assertEquals(true, openedSettings)
    }

    @Test
    fun `medications are ticked off`() {
        show(
            medications =
                listOf(
                    MedicationToday(Medication(1, "Iron", MedicationUse.DAILY), taken = true),
                    MedicationToday(Medication(2, "Ibuprofen", MedicationUse.AS_NEEDED), taken = false),
                ),
            doses = (15..19).associate { LocalDate.of(2026, 3, it) to DoseStatus.TAKEN },
        )

        compose.onNodeWithText("When needed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ibuprofen").performScrollTo().performClick()
        compose.onNodeWithText("Iron").performScrollTo().performClick()
        assertEquals(listOf(2L to true, 1L to false), taken)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB-w411dp-h1600dp")
class PhaseCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `phase cards expand to a sourced explanation`() {
        val day = LocalDate.of(2026, 3, 20)
        val settings = UserSettings(goal = Goal.BIRTH_CONTROL, onboardingComplete = true)
        val state =
            TodayUiState.Ready(
                CycleSnapshot.compute(
                    mapOf(LocalDate.of(2026, 3, 1) to app.cadence.core.domain.model.MenstrualFlow.MEDIUM),
                    settings,
                    day,
                ),
                Goal.BIRTH_CONTROL,
                5,
                DayEntry(day),
            )
        compose.setContent { CadenceTheme { TodayContent(state, {}, {}, {}, {}) } }

        compose.onNodeWithText("Learn more").performScrollTo().performClick()
        compose.onNodeWithText("withdrawal bleed", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Source: NHS", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Show less").performScrollTo().performClick()
        assertEquals(0, compose.onAllNodesWithText("Source: NHS", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `every phase has details, but not the status cards`() {
        listOf(
            R.string.phase_menstrual,
            R.string.phase_follicular,
            R.string.phase_ovulatory,
            R.string.phase_luteal,
            R.string.phase_birth_control,
        ).forEach { assertEquals(true, phaseDetails(it) != null) }
        assertEquals(null, phaseDetails(R.string.phase_late))
    }
}
