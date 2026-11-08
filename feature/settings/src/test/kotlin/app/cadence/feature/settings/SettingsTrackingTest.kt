package app.cadence.feature.settings

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Default screen size: under Robolectric, a dialog with a text field on a very tall screen never goes idle.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class SettingsTrackingTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 20)
    private val schedules = mutableListOf<ContraceptiveSchedule?>()
    private val savedMedications = mutableListOf<Medication>()
    private val deletedMedications = mutableListOf<Long>()
    private val createdTags = mutableListOf<String>()
    private val renamedTags = mutableListOf<Pair<Long, String>>()
    private val deletedTags = mutableListOf<Long>()

    private fun show(
        settings: UserSettings = UserSettings(goal = Goal.BIRTH_CONTROL, onboardingComplete = true),
        medications: List<Medication> = emptyList(),
        tags: List<CustomTag> = emptyList(),
    ) {
        compose.setContent {
            CadenceTheme {
                SettingsContent(
                    settings = settings,
                    actions =
                        SettingsActions(
                            onCycleLength = {},
                            onPeriodLength = {},
                            onGoal = {},
                            onTheme = {},
                            onHiddenCategories = {},
                            onDeleteAll = {},
                            onContraception = { schedules += it },
                            onSaveMedication = { savedMedications += it },
                            onDeleteMedication = { deletedMedications += it },
                            onCreateTag = { createdTags += it },
                            onRenameTag = { id, label -> renamedTags += id to label },
                            onDeleteTag = { deletedTags += it },
                        ),
                    tracking = TrackingSettingsState(today, medications, tags),
                )
            }
        }
    }

    @Test
    fun `a ring is set up from the day of the pack`() {
        show()

        compose.onNodeWithText("No pill, patch or ring. Tap to set one up.").performScrollTo().performClick()
        compose.onNodeWithText("Ring").performClick()
        // The dialog scrolls, so bring the stepper into view before tapping it.
        repeat(2) {
            compose
                .onNode(
                    hasContentDescription("Increase Which day of your pack is today?"),
                ).performScrollTo()
                .performClick()
        }
        compose.onNodeWithText("Day 3").assertExists()
        compose.onNodeWithText("Save").performClick()

        val ring = schedules.single()!!
        assertEquals(ContraceptiveMethod.RING, ring.method)
        assertEquals(LocalDate.of(2026, 3, 18), ring.packStart)
        assertEquals(21 to 7, ring.activeDays to ring.breakDays)
    }

    @Test
    fun `a continuous patch, and switching to something else removes the schedule`() {
        show()
        compose.onNodeWithText("No pill, patch or ring. Tap to set one up.").performScrollTo().performClick()
        compose.onNodeWithText("Patch").performClick()
        compose.onNodeWithText("Patch-free week after 3 patches").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(28 to 0, schedules.single()!!.activeDays to schedules.single()!!.breakDays)
    }

    @Test
    fun `an existing schedule shows its summary, reminder, and can be removed`() {
        val pill =
            ContraceptiveSchedule(
                ContraceptiveMethod.PILL,
                LocalDate.of(2026, 3, 2),
                24,
                4,
                placebo = true,
                reminderEnabled = true,
            )
        show(UserSettings(goal = Goal.BIRTH_CONTROL, onboardingComplete = true, contraception = pill))

        compose.onNodeWithText("Pill · 24 days, then 4 placebo days").performScrollTo().assertExists()
        compose.onNodeWithText("Birth control reminder time").performScrollTo().assertExists()
        compose.onNodeWithText("Birth control reminder").performScrollTo().performClick()
        assertEquals(false, schedules.single()!!.reminderEnabled)

        compose.onNodeWithText("Pill · 24 days, then 4 placebo days").performScrollTo().performClick()
        compose.onNodeWithText("Something else (IUD, implant, injection)").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(null, schedules.last())
    }

    @Test
    fun `on a schedule, period reminders give way to a note`() {
        val pill = ContraceptiveSchedule.typical(ContraceptiveMethod.PILL, LocalDate.of(2026, 3, 2))
        show(
            UserSettings(
                goal = Goal.BIRTH_CONTROL,
                onboardingComplete = true,
                contraception = pill,
                reminders = ReminderSettings(periodEnabled = true),
            ),
        )
        compose
            .onNodeWithText(
                "Period reminders are off while Cadence follows",
                substring = true,
            ).performScrollTo()
            .assertExists()
        assertEquals(0, compose.onAllNodesWithText("Before your period").fetchSemanticsNodes().size)
    }

    @Test
    fun `no birth control rows when tracking`() {
        show(UserSettings(onboardingComplete = true))
        assertEquals(0, compose.onAllNodesWithText("Your birth control").fetchSemanticsNodes().size)
    }

    @Test
    fun `a medication is added with a daily reminder`() {
        show()
        compose.onNodeWithText("Add a medication").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Iron")
        compose.onNodeWithText("Daily reminder").performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(listOf(Medication(0, "Iron", MedicationUse.DAILY, LocalTime.of(9, 0))), savedMedications)
    }

    @Test
    fun `a medication can be stopped or deleted`() {
        show(medications = listOf(Medication(4, "Ibuprofen", MedicationUse.AS_NEEDED)))
        compose.onNodeWithText("When needed").performScrollTo().assertExists()

        compose.onNodeWithText("Ibuprofen").performScrollTo().performClick()
        compose.onNodeWithText("Still taking it").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(false, savedMedications.single().active)

        compose.onNodeWithText("Ibuprofen").performScrollTo().performClick()
        compose.onNodeWithText("Delete medication").performClick()
        compose.onNodeWithText("Delete Ibuprofen?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf(4L), deletedMedications)
    }

    @Test
    fun `tags are added, renamed and deleted`() {
        show(tags = listOf(CustomTag(2, "Gym")))
        compose.onNodeWithText("1 tag").performScrollTo().performClick()

        compose.onNodeWithText("New tag").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Travel")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("Travel"), createdTags)

        compose.onNodeWithText("Gym").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Climbing ")
        compose.onNodeWithText("Save").performClick()
        assertEquals(2L, renamedTags.single().first)
        assertTrue(renamedTags.single().second.startsWith("Climbing"))

        compose.onNodeWithText("Gym").performClick()
        compose.onNodeWithText("Delete tag").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf(2L), deletedTags)
    }
}
