package app.cadence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.cadence.feature.onboarding.OnboardingScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object OnboardingRoute

@Serializable
internal data object MainRoute

/**
 * Switches between onboarding and the main app. Each is its own destination, so leaving
 * one clears its view models: after "Delete all data", onboarding starts fresh rather
 * than resuming the finished session.
 */
@Composable
fun CadenceRoot(
    onboardingComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val startDestination: Any = remember { if (onboardingComplete) MainRoute else OnboardingRoute }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<OnboardingRoute> { OnboardingScreen() }
        composable<MainRoute> { CadenceApp() }
    }

    LaunchedEffect(onboardingComplete) {
        val target: Any = if (onboardingComplete) MainRoute else OnboardingRoute
        val current = navController.currentDestination
        val alreadyThere =
            current != null &&
                (if (onboardingComplete) current.hasRoute<MainRoute>() else current.hasRoute<OnboardingRoute>())
        if (!alreadyThere) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }
}
