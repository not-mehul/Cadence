package app.cadence.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.cadence.core.domain.reminders.PlannedReminder
import app.cadence.core.domain.reminders.ReminderKind

/** Notification channels, so users can tune each kind of reminder in system settings. */
object ReminderChannels {
    const val PERIOD = "period_reminders"
    const val CHECK_IN = "daily_check_in"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    PERIOD,
                    context.getString(R.string.reminder_channel_period),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_period_description) },
                NotificationChannel(
                    CHECK_IN,
                    context.getString(R.string.reminder_channel_checkin),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_checkin_description) },
            ),
        )
    }

    fun forKind(kind: ReminderKind): String = if (kind == ReminderKind.DAILY_LOG) CHECK_IN else PERIOD
}

/** Whether Cadence may post notifications right now (permission granted and not blocked). */
fun canPostNotifications(context: Context): Boolean {
    val permitted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Builds and posts reminder notifications. The lock screen always shows the neutral
 * public version; with [discreet] on, the notification itself is neutral too
 * (docs/PLAN.md §2.6).
 */
object ReminderNotifier {
    fun build(
        context: Context,
        reminder: PlannedReminder,
        discreet: Boolean,
    ): android.app.Notification {
        val channel = ReminderChannels.forKind(reminder.kind)
        val neutralTitle = context.getString(R.string.reminder_discreet_title)
        val neutralText = context.getString(R.string.reminder_discreet_text)
        val (title, text) =
            if (discreet) {
                neutralTitle to neutralText
            } else {
                detailedCopy(context, reminder)
            }
        val publicVersion =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(neutralTitle)
                .setContentText(neutralText)
                .build()
        return NotificationCompat
            .Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
    }

    fun post(
        context: Context,
        reminder: PlannedReminder,
        discreet: Boolean,
    ) {
        if (!canPostNotifications(context)) return
        try {
            NotificationManagerCompat.from(context).notify(reminder.kind.ordinal, build(context, reminder, discreet))
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to show.
        }
    }

    private fun detailedCopy(
        context: Context,
        reminder: PlannedReminder,
    ): Pair<String, String> =
        when (reminder.kind) {
            ReminderKind.UPCOMING_PERIOD -> {
                val days = reminder.daysUntilPeriod ?: 1
                context.getString(R.string.reminder_upcoming_title) to
                    context.resources.getQuantityString(R.plurals.reminder_upcoming_text, days, days)
            }

            ReminderKind.LATE_PERIOD -> {
                context.getString(R.string.reminder_late_title) to
                    context.getString(R.string.reminder_late_text)
            }

            ReminderKind.DAILY_LOG -> {
                context.getString(R.string.reminder_daily_title) to
                    context.getString(R.string.reminder_daily_text)
            }
        }

    private fun openApp(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
