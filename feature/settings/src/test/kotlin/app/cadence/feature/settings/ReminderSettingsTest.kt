package app.cadence.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w411dp-h2000dp")
class ReminderSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private var saved: ReminderSettings? = null
    private var permissionRequests = 0
    private var openedNotificationSettings = 0
    private var openedBattery = 0

    private fun show(
        reminders: ReminderSettings = ReminderSettings(),
        status: ReminderStatus = ReminderStatus(),
    ) {
        compose.setContent {
            CadenceTheme {
                SettingsContent(
                    settings = UserSettings(onboardingComplete = true, reminders = reminders),
                    actions =
                        SettingsActions(
                            onCycleLength = {},
                            onPeriodLength = {},
                            onGoal = {},
                            onTheme = {},
                            onHiddenCategories = {},
                            onDeleteAll = {},
                            onReminders = { saved = it },
                            onRequestNotificationPermission = { permissionRequests++ },
                            onOpenNotificationSettings = { openedNotificationSettings++ },
                            onOpenBatterySettings = { openedBattery++ },
                        ),
                    reminderStatus = status,
                )
            }
        }
    }

    @Test
    fun `reminders are off and discreet by default`() {
        show()
        compose.onNodeWithText("Discreet notifications").performScrollTo().assertExists()
        compose.onNodeWithText("How many days before").assertDoesNotExist()
        compose.onNodeWithText("Check-in time").assertDoesNotExist()
    }

    @Test
    fun `turning a reminder on saves it and asks for permission when needed`() {
        show(status = ReminderStatus(notificationsAllowed = false))

        compose.onNodeWithText("Daily check-in").performScrollTo().performClick()

        assertEquals(ReminderSettings(dailyEnabled = true), saved)
        assertEquals(1, permissionRequests)
    }

    @Test
    fun `no permission prompt when notifications are already allowed`() {
        show()
        compose.onNodeWithText("Period coming up").performScrollTo().performClick()
        assertEquals(ReminderSettings(periodEnabled = true), saved)
        assertEquals(0, permissionRequests)
    }

    @Test
    fun `enabled reminders show their timing rows`() {
        show(ReminderSettings(periodEnabled = true, dailyEnabled = true))
        compose.onNodeWithText("How many days before").performScrollTo().assertExists()
        compose.onNodeWithText("Time for period reminders").performScrollTo().assertExists()
        compose.onNodeWithText("9:00", substring = true).assertExists()
        compose.onNodeWithText("8:00", substring = true).assertExists()
    }

    @Test
    fun `turning discreet off`() {
        show()
        compose.onNodeWithText("Discreet notifications").performScrollTo().performClick()
        assertEquals(false, saved?.discreet)
    }

    @Test
    fun `warnings link to system settings`() {
        show(
            ReminderSettings(dailyEnabled = true),
            ReminderStatus(notificationsAllowed = false, batteryRestricted = true),
        )

        compose.onNodeWithText("Turn on notifications").performScrollTo().performClick()
        compose.onNodeWithText("Battery settings").performScrollTo().performClick()

        assertEquals(1, openedNotificationSettings)
        assertEquals(1, openedBattery)
    }

    @Test
    fun `no warnings while every reminder is off`() {
        show(status = ReminderStatus(notificationsAllowed = false, batteryRestricted = true))
        compose.onNodeWithText("Turn on notifications").assertDoesNotExist()
        assertTrue(permissionRequests == 0)
    }
}
