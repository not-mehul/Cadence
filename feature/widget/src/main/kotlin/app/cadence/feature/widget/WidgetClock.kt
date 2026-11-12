package app.cadence.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps the widgets' day right across midnight. The home screen only refreshes widgets about
 * hourly, so without this "Day 12" could linger for most of an hour into day 13.
 *
 * The alarm is inexact and doesn't wake the phone: it's delivered the next time the phone is in
 * use after midnight, which is exactly when someone could look at the widget.
 */
internal object WidgetClock {
    const val ACTION_REFRESH = "app.cadence.action.WIDGET_REFRESH"

    /** A little past midnight, so the date has certainly changed. */
    private const val SECONDS_PAST_MIDNIGHT = 30L

    fun scheduleAfterMidnight(
        context: Context,
        today: LocalDate,
    ) {
        val at =
            today
                .plusDays(1)
                .atStartOfDay()
                .plusSeconds(SECONDS_PAST_MIDNIGHT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at, refreshIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    private fun refreshIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Midnight, or the user changed the clock or timezone: redraw the widgets for the new day. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED) return
        // Null when called directly rather than from a broadcast (as in tests).
        val pending: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                EntryPointAccessors
                    .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                    .updater()
                    .updateAll()
            } catch (e: Exception) {
                Log.w("CadenceWidget", "Widget refresh failed", e)
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(WidgetClock.ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    }
}
