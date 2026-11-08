package app.cadence

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.ui.theme.CadenceTheme
import dagger.hilt.android.AndroidEntryPoint

/** A FragmentActivity because the app lock uses BiometricPrompt. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash screen up until settings (theme, onboarding, lock) are loaded.
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        // Secure from the first frame; relaxed below only if the user turned it off.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (val s = state) {
                // The splash screen covers loading.
                MainUiState.Loading -> {}

                MainUiState.DataUnavailable -> {
                    CadenceTheme { DataUnavailableScreen(onStartOver = viewModel::startOver) }
                }

                MainUiState.RestartRequired -> {
                    LaunchedEffect(Unit) { restartApp() }
                }

                is MainUiState.Ready -> {
                    val darkTheme = s.theme.isDark()
                    SystemBarsFor(darkTheme)
                    ScreenSecurity(s.security.blockScreenshots)
                    CadenceTheme(darkTheme = darkTheme) {
                        // While locked, no app content is composed at all.
                        if (s.locked) {
                            LockScreen(onUnlock = viewModel::unlock)
                        } else {
                            CadenceRoot(onboardingComplete = s.onboardingComplete)
                        }
                    }
                }
            }
        }
    }

    /** Relaunches Cadence in a new process, so every singleton (the database above all) starts fresh. */
    private fun restartApp() {
        startActivity(Intent.makeRestartActivityTask(ComponentName(this, MainActivity::class.java)))
        Runtime.getRuntime().exit(0)
    }

    /** FLAG_SECURE: no screenshots, screen recording or preview in recent apps. */
    @Composable
    private fun ScreenSecurity(block: Boolean) {
        DisposableEffect(block) {
            if (block) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            onDispose {}
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
