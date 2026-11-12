package app.cadence.feature.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.ui.quick.QuickAction

/**
 * Long-press shortcuts on the launcher icon: "Log today", and "Period started" or, on a pill,
 * patch or ring schedule, "Dose done". None with the discreet icon, whose point is not to look
 * like a period tracker; any the user pinned are disabled too.
 */
object QuickShortcuts {
    /** The shortcuts that fit the user's settings; none before onboarding is done. */
    fun wanted(settings: UserSettings): List<QuickAction> =
        when {
            !settings.onboardingComplete -> emptyList()
            settings.activeSchedule != null -> listOf(QuickAction.LOG_TODAY, QuickAction.DOSE_TAKEN)
            else -> listOf(QuickAction.LOG_TODAY, QuickAction.PERIOD_STARTED)
        }

    fun publish(
        context: Context,
        actions: List<QuickAction>,
        icon: AppIcon,
    ) {
        val all = QuickAction.entries.map { it.name }
        if (icon == AppIcon.DISCREET) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            ShortcutManagerCompat.disableShortcuts(context, all, context.getString(R.string.shortcut_disabled))
            return
        }
        val shortcuts = actions.mapIndexed { rank, action -> info(context, action, rank) }
        ShortcutManagerCompat.enableShortcuts(context, shortcuts)
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun info(
        context: Context,
        action: QuickAction,
        rank: Int,
    ): ShortcutInfoCompat {
        val (short, long, icon) = action.looks()
        return ShortcutInfoCompat
            .Builder(context, action.name)
            .setShortLabel(context.getString(short))
            .setLongLabel(context.getString(long))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(action.intent(context))
            .setRank(rank)
            .build()
    }

    private data class Looks(
        @param:StringRes val short: Int,
        @param:StringRes val long: Int,
        @param:DrawableRes val icon: Int,
    )

    private fun QuickAction.looks(): Looks =
        when (this) {
            QuickAction.LOG_TODAY -> {
                Looks(R.string.shortcut_log, R.string.shortcut_log, R.drawable.shortcut_log)
            }

            QuickAction.PERIOD_STARTED -> {
                Looks(
                    R.string.shortcut_period,
                    R.string.shortcut_period,
                    R.drawable.shortcut_period,
                )
            }

            QuickAction.DOSE_TAKEN -> {
                Looks(
                    R.string.shortcut_dose,
                    R.string.shortcut_dose_long,
                    R.drawable.shortcut_dose,
                )
            }
        }
}
