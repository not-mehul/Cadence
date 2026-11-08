package app.cadence.feature.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.LocalNow
import app.cadence.core.domain.reminders.PlannedReminder
import app.cadence.core.domain.reminders.ReminderKind
import app.cadence.core.domain.reminders.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms one alarm per reminder kind for its next occurrence, and cancels the rest. Uses
 * inexact, Doze-friendly alarms, so no exact-alarm permission is needed; a reminder a few
 * minutes late is fine (docs/PLAN.md Phase 4).
 */
@Singleton
class ReminderScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val periods: PeriodRepository,
        private val settings: SettingsRepository,
        private val annotations: CycleAnnotationRepository,
        private val now: LocalNow,
    ) {
        private val alarmManager = context.getSystemService(AlarmManager::class.java)

        /** Re-plans every reminder from current data. Returns what was scheduled. */
        suspend fun reschedule(): List<PlannedReminder> {
            val planned =
                ReminderPlanner.plan(
                    flowByDay = periods.flowByDay.first(),
                    settings = settings.settings.first(),
                    excludedCycleStarts = annotations.excludedStarts.first(),
                    now = now.now(),
                )
            val byKind = planned.associateBy { it.kind }
            ReminderKind.entries.forEach { kind ->
                val reminder = byKind[kind]
                if (reminder == null) {
                    existingAlarm(kind)?.let {
                        alarmManager.cancel(it)
                        it.cancel()
                    }
                } else {
                    val triggerAt =
                        reminder.at
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        alarmIntent(kind, PendingIntent.FLAG_UPDATE_CURRENT)!!,
                    )
                }
            }
            return planned
        }

        private fun existingAlarm(kind: ReminderKind): PendingIntent? = alarmIntent(kind, PendingIntent.FLAG_NO_CREATE)

        private fun alarmIntent(
            kind: ReminderKind,
            flags: Int,
        ): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                kind.ordinal,
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ACTION_REMINDER)
                    .putExtra(EXTRA_KIND, kind.name),
                flags or PendingIntent.FLAG_IMMUTABLE,
            )

        companion object {
            const val ACTION_REMINDER = "app.cadence.action.REMINDER"
            const val EXTRA_KIND = "kind"
        }
    }
