package app.cadence.feature.log

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

// Default screen size: under Robolectric, a dialog with a text field on a very tall screen never goes idle.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class DayLogTrackingTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 20)
    private var entry = DayEntry(today)
    private val doses = mutableListOf<DoseStatus?>()
    private val taken = mutableListOf<Pair<Long, Boolean>>()
    private val created = mutableListOf<String>()

    private fun show(
        dose: DoseAction? = null,
        doseStatus: DoseStatus? = null,
        medications: List<LoggedMedication> = emptyList(),
        tags: List<CustomTag> = emptyList(),
        hidden: Set<LogCategory> = emptySet(),
    ) {
        compose.setContent {
            CadenceTheme {
                DayLogContent(
                    state = DayLogUiState.Ready(entry, true, hidden, dose, doseStatus, medications, tags),
                    onEdit = { transform -> entry = transform(entry) },
                    onNoteChange = {},
                    onDone = {},
                    tracking =
                        DayLogTrackingActions(
                            onDose = { doses += it },
                            onMedicationTaken = { id, on -> taken += id to on },
                            onCreateTag = { created += it },
                        ),
                )
            }
        }
    }

    @Test
    fun `the day's ring change is marked done, missed, or cleared`() {
        show(dose = DoseAction.REMOVE_RING, doseStatus = DoseStatus.TAKEN)

        compose.onNodeWithText("Birth control").performScrollTo().assertExists()
        compose.onNodeWithText("Take out your ring").assertExists()
        compose.onNodeWithText("Did it").assertIsSelected()
        compose.onNodeWithText("Did it").performClick()
        compose.onNodeWithText("Missed it").performClick()
        assertEquals(listOf(null, DoseStatus.MISSED), doses)
    }

    @Test
    fun `no birth control section on days without a dose`() {
        show()
        compose.onNodeWithText("Birth control").assertDoesNotExist()
    }

    @Test
    fun `tags toggle, and new ones are created from the log`() {
        show(tags = listOf(CustomTag(3, "Travel")))

        compose.onNodeWithText("Travel").performScrollTo().performClick()
        assertEquals(setOf(3L), entry.tags)

        compose.onNodeWithText("New tag").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Tag")).performTextInput("Gym")
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf("Gym"), created)
    }

    @Test
    fun `medications are shown only when there are some, and can be hidden`() {
        show(medications = listOf(LoggedMedication(Medication(5, "Iron", MedicationUse.DAILY), taken = false)))
        compose.onNodeWithText("Medications").performScrollTo().assertExists()
        compose.onNodeWithText("Iron").performScrollTo().performClick()
        assertEquals(listOf(5L to true), taken)
    }

    @Test
    fun `hidden tags and medications sections are left out`() {
        show(
            medications = listOf(LoggedMedication(Medication(5, "Iron", MedicationUse.DAILY), taken = false)),
            hidden = setOf(LogCategory.TAGS, LogCategory.MEDICATIONS),
        )
        compose.onNodeWithText("Tags").assertDoesNotExist()
        compose.onNodeWithText("Medications").assertDoesNotExist()
    }
}
