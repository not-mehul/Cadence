package app.cadence

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.reminders.ReminderKind
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.feature.reminders.ReminderChannels
import app.cadence.feature.reminders.ReminderReceiver
import app.cadence.feature.reminders.ReminderScheduler
import app.cadence.feature.reminders.RescheduleReceiver
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalTime
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reminders through the real receivers and Hilt graph (in-memory database, "now" fixed at
 * noon on 20 March 2026).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RemindersIntegrationTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms by lazy { shadowOf(context.getSystemService(AlarmManager::class.java)) }
    private val notifications by lazy { shadowOf(context.getSystemService(NotificationManager::class.java)) }

    @Before
    fun setUp() {
        hilt.inject()
        ReminderChannels.ensure(context)
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Receivers finish their work on a background thread; wait for the expected state. */
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(20)
        }
    }

    @Test
    fun `system events are routed to the reschedule receiver`() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        ).forEach { action ->
            val receivers =
                context.packageManager.queryBroadcastReceivers(
                    Intent(action).setPackage(context.packageName),
                    0,
                )
            assertTrue(receivers.any { it.activityInfo.name == RescheduleReceiver::class.java.name }, action)
        }
    }

    @Test
    fun `after a reboot the daily reminder is armed again`() {
        runBlocking {
            settings.update { it.copy(onboardingComplete = true, reminders = ReminderSettings(dailyEnabled = true)) }
        }

        RescheduleReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        waitUntil { alarms.scheduledAlarms.size == 1 }
    }

    @Test
    fun `a due reminder posts a discreet notification and arms the next one`() {
        // Check-in at 11:00; "now" is 12:00, so today's reminder is due.
        runBlocking {
            settings.update {
                it.copy(
                    reminders = ReminderSettings(dailyEnabled = true, dailyTime = LocalTime.of(11, 0)),
                )
            }
        }

        ReminderReceiver().onReceive(
            context,
            Intent(
                ReminderScheduler.ACTION_REMINDER,
            ).putExtra(ReminderScheduler.EXTRA_KIND, ReminderKind.DAILY_LOG.name),
        )

        waitUntil { notifications.allNotifications.isNotEmpty() && alarms.scheduledAlarms.isNotEmpty() }
        val posted = notifications.allNotifications.single()
        assertEquals("Time to check in", posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `a reminder turned off after its alarm was set is not shown`() {
        runBlocking { settings.update { it.copy(reminders = ReminderSettings()) } }

        ReminderReceiver().onReceive(
            context,
            Intent(
                ReminderScheduler.ACTION_REMINDER,
            ).putExtra(ReminderScheduler.EXTRA_KIND, ReminderKind.DAILY_LOG.name),
        )

        Thread.sleep(500)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, notifications.allNotifications.size)
    }
}
