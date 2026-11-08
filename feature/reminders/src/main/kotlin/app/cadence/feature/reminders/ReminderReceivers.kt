package app.cadence.feature.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.LocalNow
import app.cadence.core.domain.reminders.ReminderKind
import app.cadence.core.domain.reminders.ReminderPlanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Dependencies for the receivers, looked up from the app's Hilt component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun scheduler(): ReminderScheduler

    fun periods(): PeriodRepository

    fun settings(): SettingsRepository

    fun annotations(): CycleAnnotationRepository

    fun now(): LocalNow
}

private const val TAG = "CadenceReminders"

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Reminder work failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** An alarm went off: show the reminder (if it's still wanted) and arm the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val kind =
            intent.getStringExtra(ReminderScheduler.EXTRA_KIND)?.let { k ->
                ReminderKind.entries.firstOrNull {
                    it.name ==
                        k
                }
            }
                ?: return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        runAsync {
            val settings = deps.settings().settings.first()
            // Re-plan for "now" minus a moment, so a reminder firing exactly on time still counts.
            val current =
                ReminderPlanner
                    .plan(
                        deps.periods().flowByDay.first(),
                        settings,
                        deps.annotations().excludedStarts.first(),
                        deps.now().now().minusMinutes(STALE_AFTER_MINUTES),
                    ).firstOrNull { it.kind == kind }
            // Only notify if this reminder is still on and still due (data may have changed since it was armed).
            if (current != null && !current.at.isAfter(deps.now().now())) {
                ReminderNotifier.post(context, current, settings.reminders.discreet)
            }
            deps.scheduler().reschedule()
        }
    }

    private companion object {
        /** An alarm delivered up to this late (Doze, battery savers) is still shown. */
        const val STALE_AFTER_MINUTES = 12 * 60L
    }
}

/** Reboot, app update, or clock/timezone change: alarms are gone or wrong, so re-arm them. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED_ACTIONS) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        runAsync { deps.scheduler().reschedule() }
    }

    private companion object {
        val HANDLED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}
