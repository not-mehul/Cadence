package app.cadence

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.cadence.feature.calendar.CalendarScreen
import app.cadence.feature.insights.InsightsScreen
import app.cadence.feature.log.DayLogSheet
import app.cadence.feature.settings.SettingsScreen
import app.cadence.feature.today.TodayScreen
import app.cadence.navigation.CalendarRoute
import app.cadence.navigation.InsightsRoute
import app.cadence.navigation.SettingsRoute
import app.cadence.navigation.TodayRoute
import app.cadence.navigation.TopLevelDestination
import java.time.LocalDate

/**
 * App shell: a bottom navigation bar on phones, switching to a navigation rail on
 * tablets and unfolded foldables.
 */
@Composable
fun CadenceApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // The day whose log sheet is open, as an epoch day so it survives rotation and process death.
    var logEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val openLog: (LocalDate) -> Unit = { logEpochDay = it.toEpochDay() }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    item(
                        selected = currentDestination.isOn(destination),
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            },
        ) {
            NavHost(
                navController = navController,
                startDestination = TodayRoute,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            ) {
                composable<TodayRoute> {
                    TodayScreen(
                        onOpenLog = openLog,
                        onOpenSettings = { navController.navigateToTopLevel(TopLevelDestination.SETTINGS) },
                    )
                }
                composable<CalendarRoute> { CalendarScreen(onOpenLog = openLog) }
                composable<InsightsRoute> { InsightsScreen() }
                composable<SettingsRoute> { SettingsScreen() }
            }
        }
    }

    logEpochDay?.let { day ->
        DayLogSheet(date = LocalDate.ofEpochDay(day), onDismiss = { logEpochDay = null })
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any(destination.matches) == true

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Standard tab behaviour: one copy of each tab, state kept when switching.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
