package app.cadence.feature.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import app.cadence.core.data.repository.ContraceptionRepository
import app.cadence.core.data.repository.MedicationRepository
import app.cadence.core.data.time.LocalNow
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.reminders.ReminderKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Dependencies for the receivers, looked up from the app's Hilt component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun scheduler(): ReminderScheduler

    fun plans(): ReminderPlans

    fun contraception(): ContraceptionRepository

    fun medications(): MedicationRepository

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

private fun Context.reminderDeps() =
    EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)

/** An alarm went off: show the reminder (if it's still wanted) and arm the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val key = ReminderKey.from(intent) ?: return
        val deps = context.reminderDeps()
        runAsync {
            // Re-plan for "now" minus a moment, so a reminder firing exactly on time still counts.
            val snapshot = deps.plans().at(deps.now().now().minusMinutes(STALE_AFTER_MINUTES))
            val current = snapshot.planned.firstOrNull { ReminderKey.of(it) == key }
            // Only notify if this reminder is still on, still due, and its dose isn't already recorded.
            if (current != null && !current.at.isAfter(deps.now().now())) {
                val name = snapshot.medications.firstOrNull { it.id == current.medicationId }?.name
                ReminderNotifier.post(context, current, snapshot.settings.discreet, name)
            }
            deps.scheduler().reschedule()
        }
    }

    private companion object {
        /** An alarm delivered up to this late (Doze, battery savers) is still shown. */
        const val STALE_AFTER_MINUTES = 12 * 60L
    }
}

/** "Done" on a birth control or medication reminder: record the dose without opening the app. */
class DoseDoneReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_DONE) return
        val key = ReminderKey.from(intent) ?: return
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        val deps = context.reminderDeps()
        NotificationManagerCompat.from(context).cancel(key.requestCode)
        runAsync {
            when (key.kind) {
                ReminderKind.CONTRACEPTIVE -> deps.contraception().record(date, DoseStatus.TAKEN)
                ReminderKind.MEDICATION -> key.medicationId?.let { deps.medications().setTaken(date, it, true) }
                else -> Unit
            }
            deps.scheduler().reschedule()
        }
    }

    companion object {
        const val ACTION_DONE = "app.cadence.action.DOSE_DONE"
        const val EXTRA_DATE = "date"
    }
}

/** Reboot, app update, or clock/timezone change: alarms are gone or wrong, so re-arm them. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED_ACTIONS) return
        val deps = context.reminderDeps()
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
