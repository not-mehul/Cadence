package app.cadence.feature.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.cadence.core.data.backup.BackupData
import app.cadence.core.data.backup.BackupException
import app.cadence.core.data.backup.ImportMode
import app.cadence.core.domain.log.DayEntry
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
@Config(qualifiers = "en-rUS")
class BackupDialogsTest {
    @get:Rule
    val compose = createComposeRule()

    private var password: String? = null
    private var mode: ImportMode? = null
    private var cancelled = 0
    private var dismissed = 0

    private fun show(
        state: BackupUiState,
        askForMode: Boolean = true,
    ) {
        compose.setContent {
            CadenceTheme {
                BackupDialogs(
                    state,
                    BackupDialogActions(
                        askForMode = askForMode,
                        onPassword = { password = String(it) },
                        onMode = { mode = it },
                        onCancelImport = { cancelled++ },
                        onDismissMessage = { dismissed++ },
                    ),
                )
            }
        }
    }

    private val data =
        BackupData(
            UserSettings(onboardingComplete = true),
            (1..3).map { DayEntry(LocalDate.of(2026, 3, it), note = "n") },
            emptySet(),
        )

    @Test
    fun `an encrypted backup asks for its password`() {
        show(BackupUiState(pendingImport = PendingImport.NeedsPassword(ByteArray(0))))

        compose.onNodeWithText("Open backup").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("hunter22")
        compose.onNodeWithText("Open backup").performClick()
        assertEquals("hunter22", password)
    }

    @Test
    fun `a wrong password is pointed out`() {
        show(BackupUiState(pendingImport = PendingImport.NeedsPassword(ByteArray(0), wrongPassword = true)))

        compose.onNodeWithText("That password doesn't open this backup.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `restoring asks whether to replace or merge`() {
        show(BackupUiState(pendingImport = PendingImport.ChooseMode(data)))

        compose.onNodeWithText("3 logged days", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Merge").performClick()
        assertEquals(ImportMode.MERGE, mode)
    }

    @Test
    fun `during onboarding the mode isn't asked`() {
        show(BackupUiState(pendingImport = PendingImport.ChooseMode(data)), askForMode = false)

        assertEquals(
            0,
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Merge"),
                ).fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `failures are explained`() {
        show(BackupUiState(message = BackupMessage.ImportFailed(BackupException.Reason.NEWER_VERSION)))

        compose.onNodeWithText("newer", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `the report asks how far back to look, six months by default`() {
        var chosen: ReportRange? = null
        compose.setContent {
            CadenceTheme {
                ReportRangeDialog(
                    initial = ReportRange.DEFAULT,
                    onContinue = { chosen = it },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("What should the report cover?").assertIsDisplayed()
        compose.onNodeWithText("The last 6 months").assertIsSelected()
        compose.onNodeWithText("The last 12 months").performClick()
        compose.onNodeWithText("Choose where to save").performClick()

        assertEquals(ReportRange.MONTHS_12, chosen)
    }
}
