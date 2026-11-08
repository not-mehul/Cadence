package app.cadence.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import app.cadence.R
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object CalendarRoute

@Serializable
data object InsightsRoute

@Serializable
data object SettingsRoute

/** The four tabs (docs/PLAN.md §6). */
enum class TopLevelDestination(
    val route: Any,
    /** Whether a destination in the back stack belongs to this tab. */
    val matches: (NavDestination) -> Boolean,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    TODAY(TodayRoute, { it.hasRoute<TodayRoute>() }, R.string.nav_today, R.drawable.ic_nav_today),
    CALENDAR(CalendarRoute, { it.hasRoute<CalendarRoute>() }, R.string.nav_calendar, R.drawable.ic_nav_calendar),
    INSIGHTS(InsightsRoute, { it.hasRoute<InsightsRoute>() }, R.string.nav_insights, R.drawable.ic_nav_insights),
    SETTINGS(SettingsRoute, { it.hasRoute<SettingsRoute>() }, R.string.nav_settings, R.drawable.ic_nav_settings),
}
