package app.cadence.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The launcher icons Cadence can show (activity-aliases in the app manifest). */
enum class AppIcon(
    /** The alias class name, relative to the app's namespace (not its id, which differs in debug builds). */
    val alias: String,
) {
    STANDARD("app.cadence.LauncherDefault"),

    /** "Planner" with a plain notepad icon. */
    DISCREET("app.cadence.LauncherDiscreet"),
}

/** Reads and switches the enabled launcher alias. */
object AppIcons {
    private fun component(
        context: Context,
        icon: AppIcon,
    ) = ComponentName(context.packageName, icon.alias)

    fun current(context: Context): AppIcon {
        val state = context.packageManager.getComponentEnabledSetting(component(context, AppIcon.DISCREET))
        return if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) AppIcon.DISCREET else AppIcon.STANDARD
    }

    /** Enables [icon] before disabling the other, so there's never a moment with no way to open the app. */
    fun set(
        context: Context,
        icon: AppIcon,
    ) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            component(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(
                component(context, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
