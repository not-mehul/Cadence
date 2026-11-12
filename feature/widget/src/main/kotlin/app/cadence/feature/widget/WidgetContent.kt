package app.cadence.feature.widget

import android.content.Context
import android.text.format.DateFormat
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.cycles.CycleStatus
import app.cadence.core.domain.cycles.CycleTimeline
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.ui.format.formatHeading
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The widget's two lines. */
data class WidgetText(
    val primary: String,
    val secondary: String,
)

/** The large widget: a ring beside the text, which can then drop the day number. */
data class LargeWidget(
    val primary: String,
    val secondary: String?,
    /** Null while locked and before there's data. */
    val ring: WidgetRing?,
    /** Under the day in the ring's middle: "day" (discreet) or "of 28". */
    val ringCaption: String? = null,
    /** Read out for the ring, e.g. "Cycle day 20". */
    val ringDescription: String? = null,
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
            return WidgetText(context.getString(R.string.widget_day, day), dateHeading(snapshot, locale))
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

    /**
     * The large widget, with the same privacy rules: nothing while locked; discreet shows a plain
     * progress ring and the date; otherwise the ring shows the phases (or the pack and its break).
     */
    fun large(
        context: Context,
        settings: UserSettings,
        snapshot: CycleSnapshot,
        locale: Locale = Locale.getDefault(),
    ): LargeWidget {
        val text = build(context, settings, snapshot, locale)
        val day = snapshot.pack?.dayOfPack ?: snapshot.cycleDay
        val schedule = settings.activeSchedule.takeIf { snapshot.pack != null }
        val cycleLength = schedule?.packLength ?: snapshot.timeline?.cycleLength
        if (settings.security.lockEnabled || !settings.onboardingComplete || day == null || cycleLength == null) {
            return LargeWidget(text.primary, text.secondary, ring = null)
        }
        // A late period runs past the expected length; the ring stretches to fit.
        val length = maxOf(cycleLength, day)
        if (settings.reminders.discreet) {
            return LargeWidget(
                primary = dateHeading(snapshot, locale),
                secondary = null,
                ring = WidgetRing(day, length, listOf(WidgetArc(1, day, ArcRole.PROGRESS))),
                ringCaption = context.getString(R.string.widget_ring_day),
                ringDescription = text.primary,
            )
        }
        val arcs =
            if (schedule != null) {
                listOfNotNull(
                    WidgetArc(1, schedule.activeDays, ArcRole.ACTIVE),
                    WidgetArc(schedule.activeDays + 1, schedule.packLength, ArcRole.BREAK).takeIf { schedule.hasBreak },
                )
            } else {
                phaseArcs(requireNotNull(snapshot.timeline))
            }
        return LargeWidget(
            primary = text.primary,
            secondary = dateHeading(snapshot, locale),
            ring = WidgetRing(day, length, arcs),
            ringCaption = context.getString(R.string.widget_ring_of, cycleLength),
            ringDescription = text.secondary,
        )
    }

    /** As on Today's ring: period, then (with ovulation estimates) follicular, fertile and luteal days. */
    internal fun phaseArcs(timeline: CycleTimeline): List<WidgetArc> {
        val length = timeline.cycleLength
        val periodEnd = timeline.periodEndDay.coerceAtMost(length)
        val arcs = mutableListOf(WidgetArc(1, periodEnd, ArcRole.PERIOD))
        val fertile = timeline.fertileWindowDays ?: return arcs
        val fertileStart = maxOf(fertile.first, periodEnd + 1)
        val fertileEnd = minOf(fertile.last, length)
        if (fertileStart > periodEnd + 1) arcs += WidgetArc(periodEnd + 1, fertileStart - 1, ArcRole.FOLLICULAR)
        if (fertileEnd >= fertileStart) arcs += WidgetArc(fertileStart, fertileEnd, ArcRole.FERTILE)
        val lutealStart = maxOf(fertileEnd, periodEnd) + 1
        if (lutealStart <= length) arcs += WidgetArc(lutealStart, length, ArcRole.LUTEAL)
        return arcs
    }

    private fun dateHeading(
        snapshot: CycleSnapshot,
        locale: Locale,
    ): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        return DateTimeFormatter.ofPattern(pattern, locale).formatHeading(snapshot.today)
    }
}
