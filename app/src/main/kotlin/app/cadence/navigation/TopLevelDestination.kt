package app.cadence.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import app.cadence.R
import kotlinx.serialization.Serializable
import app.cadence.core.ui.R as UiR

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
    /** Filled while the tab is selected, outlined otherwise. */
    @param:DrawableRes val selectedIcon: Int,
) {
    TODAY(
        TodayRoute,
        { it.hasRoute<TodayRoute>() },
        R.string.nav_today,
        UiR.drawable.ms_home,
        UiR.drawable.ms_home_fill1,
    ),
    CALENDAR(
        CalendarRoute,
        { it.hasRoute<CalendarRoute>() },
        R.string.nav_calendar,
        UiR.drawable.ms_calendar_month,
        UiR.drawable.ms_calendar_month_fill1,
    ),
    INSIGHTS(
        InsightsRoute,
        { it.hasRoute<InsightsRoute>() },
        R.string.nav_insights,
        UiR.drawable.ms_insights,
        UiR.drawable.ms_insights,
    ),
    SETTINGS(
        SettingsRoute,
        { it.hasRoute<SettingsRoute>() },
        R.string.nav_settings,
        UiR.drawable.ms_settings,
        UiR.drawable.ms_settings_fill1,
    ),
}
