package app.cadence.feature.widget

import android.content.Context
import android.text.format.DateFormat
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.cycles.CycleStatus
import app.cadence.core.domain.model.UserSettings
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The widget's two lines. */
data class WidgetText(
    val primary: String,
    val secondary: String,
)

/**
 * What the home-screen widget says (docs/PLAN.md Phase 7). Anyone can see a home screen, so:
 * with app lock on it shows nothing at all; with discreet notifications on (the default) it
 * shows only the day number and the date, never the word "period".
 */
object WidgetContent {
    fun build(
        context: Context,
        settings: UserSettings,
        snapshot: CycleSnapshot,
        locale: Locale = Locale.getDefault(),
    ): WidgetText {
        if (settings.security.lockEnabled) {
            return WidgetText(
                context.getString(R.string.widget_locked),
                context.getString(R.string.widget_locked_detail),
            )
        }
        val day = snapshot.pack?.dayOfPack ?: snapshot.cycleDay
        if (!settings.onboardingComplete || day == null) {
            return WidgetText(context.getString(R.string.widget_start), context.getString(R.string.widget_start_detail))
        }
        if (settings.reminders.discreet) {
            val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
            return WidgetText(
                context.getString(R.string.widget_day, day),
                DateTimeFormatter.ofPattern(pattern, locale).format(snapshot.today),
            )
        }
        val res = context.resources
        val primary =
            when (val status = snapshot.status) {
                CycleStatus.NoData -> {
                    context.getString(R.string.widget_start)
                }

                is CycleStatus.OnPeriod -> {
                    context.getString(R.string.widget_on_period, status.dayOfPeriod)
                }

                is CycleStatus.BetweenPeriods -> {
                    if (status.daysUntil == 0) {
                        context.getString(R.string.widget_period_today)
                    } else {
                        res.getQuantityString(R.plurals.widget_period_in, status.daysUntil, status.daysUntil)
                    }
                }

                is CycleStatus.Due -> {
                    context.getString(R.string.widget_period_due)
                }

                is CycleStatus.Late -> {
                    res.getQuantityString(R.plurals.widget_period_late, status.daysLate, status.daysLate)
                }

                is CycleStatus.PackActive -> {
                    status.daysUntilBreak?.let { res.getQuantityString(R.plurals.widget_break_in, it, it) }
                        ?: context.getString(R.string.widget_pack_day, status.dayOfPack)
                }

                is CycleStatus.PackBreak -> {
                    context.getString(R.string.widget_break_day, status.dayOfBreak, status.breakDays)
                }
            }
        val secondary =
            context.getString(if (snapshot.pack != null) R.string.widget_pack_day else R.string.widget_cycle_day, day)
        return WidgetText(primary, secondary)
    }
}
