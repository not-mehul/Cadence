package app.cadence.feature.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.cadence.core.data.time.LocalNow
import app.cadence.core.domain.reminders.PlannedReminder
import app.cadence.core.domain.reminders.ReminderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms one alarm per reminder (one per kind, and one per medication) for its next
 * occurrence, and cancels the rest. Uses inexact, Doze-friendly alarms, so no exact-alarm
 * permission is needed; a reminder a few minutes late is fine.
 */
@Singleton
class ReminderScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val plans: ReminderPlans,
        private val now: LocalNow,
    ) {
        private val alarmManager = context.getSystemService(AlarmManager::class.java)

        /** Medications seen at the last reschedule, so a deleted one's alarm can still be found and cancelled. */
        private var knownMedicationIds: Set<Long> = emptySet()

        /** Re-plans every reminder from current data. Returns what was scheduled. */
        suspend fun reschedule(): List<PlannedReminder> {
            val snapshot = plans.at(now.now())
            val planned = snapshot.planned.associateBy { ReminderKey(it.kind, it.medicationId) }
            // Every alarm that could exist: one per kind, plus one per medication (stopped ones included,
            // and ones deleted since the last reschedule).
            val medicationIds = snapshot.medications.map { it.id }.toSet()
            val keys =
                ReminderKind.entries.filter { it != ReminderKind.MEDICATION }.map { ReminderKey(it, null) } +
                    (medicationIds + knownMedicationIds).map { ReminderKey(ReminderKind.MEDICATION, it) }
            val deleted = knownMedicationIds - medicationIds
            knownMedicationIds = medicationIds
            keys.forEach { key ->
                val reminder = planned[key]
                if (reminder == null) {
                    alarmIntent(key, PendingIntent.FLAG_NO_CREATE)?.let {
                        alarmManager.cancel(it)
                        it.cancel()
                    }
                    // A deleted medication's reminder shouldn't linger in the shade either.
                    if (key.medicationId in deleted) NotificationManagerCompat.from(context).cancel(key.requestCode)
                } else {
                    val triggerAt =
                        reminder.at
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        alarmIntent(key, PendingIntent.FLAG_UPDATE_CURRENT)!!,
                    )
                }
            }
            return snapshot.planned
        }

        private fun alarmIntent(
            key: ReminderKey,
            flags: Int,
        ): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                key.requestCode,
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ACTION_REMINDER)
                    .putExtra(EXTRA_KIND, key.kind.name)
                    .apply { key.medicationId?.let { putExtra(EXTRA_MEDICATION_ID, it) } },
                flags or PendingIntent.FLAG_IMMUTABLE,
            )

        companion object {
            const val ACTION_REMINDER = "app.cadence.action.REMINDER"
            const val EXTRA_KIND = "kind"
            const val EXTRA_MEDICATION_ID = "medication_id"
        }
    }

/** Identifies one alarm (and its notification): a kind, plus the medication for medication reminders. */
data class ReminderKey(
    val kind: ReminderKind,
    val medicationId: Long?,
) {
    /** Unique per alarm, for PendingIntent request codes and notification ids. */
    val requestCode: Int
        get() = if (medicationId == null) kind.ordinal else (MEDICATION_BASE + medicationId).toInt()

    companion object {
        /** Medication alarms come after the fixed kinds. */
        private const val MEDICATION_BASE = 1_000L

        fun of(reminder: PlannedReminder) = ReminderKey(reminder.kind, reminder.medicationId)

        fun from(intent: Intent): ReminderKey? {
            val kind =
                intent.getStringExtra(ReminderScheduler.EXTRA_KIND)?.let { name ->
                    ReminderKind.entries.firstOrNull { it.name == name }
                } ?: return null
            val medicationId =
                intent.getLongExtra(ReminderScheduler.EXTRA_MEDICATION_ID, -1L).takeIf { it >= 0 }
            return ReminderKey(kind, medicationId)
        }
    }
}
