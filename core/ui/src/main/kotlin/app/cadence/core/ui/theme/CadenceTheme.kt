package app.cadence.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The Cadence theme. Colours are fixed (no Material You dynamic colour) so cycle
 * colours always mean the same thing (docs/PLAN.md §9, decision 4).
 */
@Composable
fun CadenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCycleColors provides if (darkTheme) DarkCycleColors else LightCycleColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) CadenceDarkColorScheme else CadenceLightColorScheme,
            typography = CadenceTypography,
            shapes = CadenceShapes,
            content = content,
        )
    }
}

/** Accessors for Cadence-specific theme values, alongside [MaterialTheme]. */
object CadenceTheme {
    val cycleColors: CycleColors
        @Composable @ReadOnlyComposable
        get() = LocalCycleColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current
}
