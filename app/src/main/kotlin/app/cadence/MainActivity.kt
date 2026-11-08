package app.cadence

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.ui.theme.CadenceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash screen up until settings (theme, onboarding) are loaded, to avoid a flash.
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? MainUiState.Ready ?: return@setContent
            val darkTheme = ready.theme.isDark()
            SystemBarsFor(darkTheme)
            CadenceTheme(darkTheme = darkTheme) {
                CadenceRoot(onboardingComplete = ready.onboardingComplete)
            }
        }
    }

    /** Status and navigation bar icons follow the app's theme, which may differ from the system's. */
    @Composable
    private fun SystemBarsFor(darkTheme: Boolean) {
        DisposableEffect(darkTheme) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }
    }
}

@Composable
private fun ThemePreference.isDark(): Boolean =
    when (this) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
