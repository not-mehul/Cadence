package app.cadence.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.reminders.PlannedReminder
import app.cadence.core.domain.reminders.ReminderKind

/** Notification channels, so users can tune each kind of reminder in system settings. */
object ReminderChannels {
    const val PERIOD = "period_reminders"
    const val CHECK_IN = "daily_check_in"
    const val MEDICATION = "medication_reminders"

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
                NotificationChannel(
                    MEDICATION,
                    context.getString(R.string.reminder_channel_medication),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_medication_description) },
            ),
        )
    }

    fun forKind(kind: ReminderKind): String =
        when (kind) {
            ReminderKind.UPCOMING_PERIOD, ReminderKind.LATE_PERIOD -> PERIOD
            ReminderKind.DAILY_LOG -> CHECK_IN
            ReminderKind.CONTRACEPTIVE, ReminderKind.MEDICATION -> MEDICATION
        }
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
    /** [medicationName] is shown in a detailed (not discreet) medication reminder. */
    fun build(
        context: Context,
        reminder: PlannedReminder,
        discreet: Boolean,
        medicationName: String? = null,
    ): android.app.Notification {
        val channel = ReminderChannels.forKind(reminder.kind)
        val neutralTitle = context.getString(R.string.reminder_discreet_title)
        val neutralText = context.getString(R.string.reminder_discreet_text)
        val (title, text) =
            if (discreet) {
                neutralTitle to neutralText
            } else {
                detailedCopy(context, reminder, medicationName)
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
            .apply {
                // Doses can be checked off straight from the notification; "Done" gives nothing away.
                doneIntent(
                    context,
                    reminder,
                )?.let { addAction(0, context.getString(R.string.reminder_action_done), it) }
            }.build()
    }

    private fun doneIntent(
        context: Context,
        reminder: PlannedReminder,
    ): PendingIntent? {
        if (reminder.kind != ReminderKind.CONTRACEPTIVE && reminder.kind != ReminderKind.MEDICATION) return null
        val key = ReminderKey.of(reminder)
        val intent =
            Intent(context, DoseDoneReceiver::class.java)
                .setAction(DoseDoneReceiver.ACTION_DONE)
                .putExtra(ReminderScheduler.EXTRA_KIND, key.kind.name)
                .putExtra(DoseDoneReceiver.EXTRA_DATE, reminder.date.toString())
                .apply { key.medicationId?.let { putExtra(ReminderScheduler.EXTRA_MEDICATION_ID, it) } }
        return PendingIntent.getBroadcast(
            context,
            key.requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun post(
        context: Context,
        reminder: PlannedReminder,
        discreet: Boolean,
        medicationName: String? = null,
    ) {
        if (!canPostNotifications(context)) return
        try {
            NotificationManagerCompat
                .from(context)
                .notify(ReminderKey.of(reminder).requestCode, build(context, reminder, discreet, medicationName))
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to show.
        }
    }

    private fun detailedCopy(
        context: Context,
        reminder: PlannedReminder,
        medicationName: String?,
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

            ReminderKind.CONTRACEPTIVE -> {
                context.getString(R.string.reminder_dose_title) to context.getString(doseText(reminder.action))
            }

            ReminderKind.MEDICATION -> {
                context.getString(R.string.reminder_medication_title) to
                    context.getString(R.string.reminder_medication_text, medicationName ?: "")
            }
        }

    @androidx.annotation.StringRes
    private fun doseText(action: DoseAction?): Int =
        when (action) {
            DoseAction.ACTIVE_PILL, null -> R.string.reminder_dose_pill
            DoseAction.PLACEBO_PILL -> R.string.reminder_dose_placebo
            DoseAction.NEW_PATCH -> R.string.reminder_dose_new_patch
            DoseAction.REMOVE_PATCH -> R.string.reminder_dose_remove_patch
            DoseAction.NEW_RING -> R.string.reminder_dose_new_ring
            DoseAction.REMOVE_RING -> R.string.reminder_dose_remove_ring
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
