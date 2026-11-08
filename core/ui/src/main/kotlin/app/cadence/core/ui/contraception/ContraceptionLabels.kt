package app.cadence.core.ui.contraception

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.ui.R

/** Wording for birth control schedules and doses, shared by Today, the log and Settings. */
object ContraceptionLabels {
    @StringRes
    fun method(method: ContraceptiveMethod): Int =
        when (method) {
            ContraceptiveMethod.PILL -> R.string.bc_method_pill
            ContraceptiveMethod.PATCH -> R.string.bc_method_patch
            ContraceptiveMethod.RING -> R.string.bc_method_ring
        }

    /** What to do, as a short task: "Take today's pill". */
    @StringRes
    fun task(action: DoseAction): Int =
        when (action) {
            DoseAction.ACTIVE_PILL -> R.string.bc_task_pill
            DoseAction.PLACEBO_PILL -> R.string.bc_task_placebo
            DoseAction.NEW_PATCH -> R.string.bc_task_new_patch
            DoseAction.REMOVE_PATCH -> R.string.bc_task_remove_patch
            DoseAction.NEW_RING -> R.string.bc_task_new_ring
            DoseAction.REMOVE_RING -> R.string.bc_task_remove_ring
        }

    /** The dose as a noun, for days other than today: "Pill", "New ring", "Ring out". */
    @StringRes
    fun noun(action: DoseAction): Int =
        when (action) {
            DoseAction.ACTIVE_PILL -> R.string.bc_noun_pill
            DoseAction.PLACEBO_PILL -> R.string.bc_noun_placebo
            DoseAction.NEW_PATCH -> R.string.bc_noun_new_patch
            DoseAction.REMOVE_PATCH -> R.string.bc_noun_remove_patch
            DoseAction.NEW_RING -> R.string.bc_noun_new_ring
            DoseAction.REMOVE_RING -> R.string.bc_noun_remove_ring
        }

    /** What was done, for a checked-off dose: "Pill taken". */
    @StringRes
    fun done(action: DoseAction): Int =
        when (action) {
            DoseAction.ACTIVE_PILL -> R.string.bc_done_pill
            DoseAction.PLACEBO_PILL -> R.string.bc_done_placebo
            DoseAction.NEW_PATCH -> R.string.bc_done_new_patch
            DoseAction.REMOVE_PATCH -> R.string.bc_done_remove_patch
            DoseAction.NEW_RING -> R.string.bc_done_new_ring
            DoseAction.REMOVE_RING -> R.string.bc_done_remove_ring
        }

    /** "Pill · 21 days, then a 7-day break" and similar. */
    @Composable
    fun summary(schedule: ContraceptiveSchedule): String {
        val method = stringResource(method(schedule.method))
        val active = pluralStringResource(R.plurals.days_count, schedule.activeDays, schedule.activeDays)
        val layout =
            when {
                !schedule.hasBreak -> {
                    stringResource(R.string.bc_layout_continuous)
                }

                schedule.placebo -> {
                    pluralStringResource(
                        R.plurals.bc_layout_placebo,
                        schedule.breakDays,
                        active,
                        schedule.breakDays,
                    )
                }

                else -> {
                    pluralStringResource(R.plurals.bc_layout_break, schedule.breakDays, active, schedule.breakDays)
                }
            }
        return stringResource(R.string.bc_summary, method, layout)
    }
}
