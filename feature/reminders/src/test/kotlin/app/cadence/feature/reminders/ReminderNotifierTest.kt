package app.cadence.feature.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.reminders.PlannedReminder
import app.cadence.core.domain.reminders.ReminderKind
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDateTime
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ReminderNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val upcoming =
        PlannedReminder(ReminderKind.UPCOMING_PERIOD, LocalDateTime.of(2026, 3, 27, 9, 0), daysUntilPeriod = 2)

    @Before
    fun setUp() {
        ReminderChannels.ensure(context)
    }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun `discreet notifications say nothing personal`() {
        ReminderKind.entries.forEach { kind ->
            val n = ReminderNotifier.build(context, upcoming.copy(kind = kind), discreet = true)
            assertEquals("Cadence", n.title())
            assertEquals("Time to check in", n.text())
        }
    }

    @Test
    fun `detailed notifications describe the reminder`() {
        assertEquals(
            "Your period is likely to start in 2 days.",
            ReminderNotifier.build(context, upcoming, discreet = false).text(),
        )
        assertEquals(
            "Period running late",
            ReminderNotifier.build(context, upcoming.copy(kind = ReminderKind.LATE_PERIOD), discreet = false).title(),
        )
        assertEquals(
            "How are you feeling today? Tap to log.",
            ReminderNotifier.build(context, upcoming.copy(kind = ReminderKind.DAILY_LOG), discreet = false).text(),
        )
    }

    @Test
    fun `dose reminders say what to do, and can be checked off`() {
        val ring = upcoming.copy(kind = ReminderKind.CONTRACEPTIVE, action = DoseAction.REMOVE_RING)
        val n = ReminderNotifier.build(context, ring, discreet = false)
        assertEquals("Time to take out your ring for the ring-free week.", n.text())
        assertEquals(ReminderChannels.MEDICATION, n.channelId)
        assertEquals(listOf("Done"), n.actions.map { it.title.toString() })

        val iron = upcoming.copy(kind = ReminderKind.MEDICATION, medicationId = 4)
        assertEquals(
            "Time for Iron.",
            ReminderNotifier.build(context, iron, discreet = false, medicationName = "Iron").text(),
        )
        // Discreet: no name, still a neutral "Done".
        val discreet = ReminderNotifier.build(context, iron, discreet = true, medicationName = "Iron")
        assertEquals("Time to check in", discreet.text())
        assertEquals(listOf("Done"), discreet.actions.map { it.title.toString() })

        // Period and check-in reminders have nothing to check off.
        assertEquals(null, ReminderNotifier.build(context, upcoming, discreet = false).actions)
    }

    @Test
    fun `every dose action has its own text`() {
        val texts =
            DoseAction.entries.map { action ->
                ReminderNotifier
                    .build(
                        context,
                        upcoming.copy(kind = ReminderKind.CONTRACEPTIVE, action = action),
                        false,
                    ).text()
            }
        assertEquals(DoseAction.entries.size, texts.toSet().size)
    }

    @Test
    fun `the lock screen always gets the neutral version`() {
        val n = ReminderNotifier.build(context, upcoming, discreet = false)

        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, n.visibility)
        assertEquals("Cadence", n.publicVersion.title())
        assertEquals("Time to check in", n.publicVersion.text())
    }

    @Test
    fun `channels separate period reminders, the daily check-in, and doses`() {
        assertEquals(ReminderChannels.PERIOD, ReminderNotifier.build(context, upcoming, true).channelId)
        assertEquals(
            ReminderChannels.CHECK_IN,
            ReminderNotifier.build(context, upcoming.copy(kind = ReminderKind.DAILY_LOG), true).channelId,
        )
        assertEquals(
            setOf(ReminderChannels.PERIOD, ReminderChannels.CHECK_IN, ReminderChannels.MEDICATION),
            manager.notificationChannels.map { it.id }.toSet(),
        )
    }

    @Test
    fun `nothing is posted without notification permission`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifier.post(context, upcoming, discreet = true)
        assertEquals(0, shadowOf(manager).allNotifications.size)

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifier.post(context, upcoming, discreet = true)
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
}
