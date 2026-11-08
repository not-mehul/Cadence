package app.cadence.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertTrue

/**
 * Checks the fixed Cadence palette against WCAG 2.1 AA (docs/PLAN.md §6.1):
 * 4.5:1 for text on its background, 3:1 for non-text UI such as the cycle ring,
 * calendar markers and outlines.
 */
class ContrastTest {
    @Test
    fun `light scheme text pairs meet AA`() = assertTextPairs(CadenceLightColorScheme)

    @Test
    fun `dark scheme text pairs meet AA`() = assertTextPairs(CadenceDarkColorScheme)

    @Test
    fun `light cycle colours are visible against the surface`() =
        assertCycleColours(LightCycleColors, CadenceLightColorScheme)

    @Test
    fun `dark cycle colours are visible against the surface`() =
        assertCycleColours(DarkCycleColors, CadenceDarkColorScheme)

    private fun assertTextPairs(s: ColorScheme) {
        val surfaces =
            listOf(
                s.surface,
                s.surfaceContainerLowest,
                s.surfaceContainerLow,
                s.surfaceContainer,
                s.surfaceContainerHigh,
                s.surfaceContainerHighest,
            )
        val pairs =
            buildList {
                add("onPrimary/primary" to (s.onPrimary to s.primary))
                add("onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer))
                add("onSecondary/secondary" to (s.onSecondary to s.secondary))
                add("onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer))
                add("onTertiary/tertiary" to (s.onTertiary to s.tertiary))
                add("onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer))
                add("onError/error" to (s.onError to s.error))
                add("onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer))
                add("onBackground/background" to (s.onBackground to s.background))
                add("onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant))
                add("inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface))
                surfaces.forEachIndexed { i, bg ->
                    add("onSurface/surface#$i" to (s.onSurface to bg))
                    add("onSurfaceVariant/surface#$i" to (s.onSurfaceVariant to bg))
                    // Primary and error are used as text colours (section headers, error text).
                    add("primary/surface#$i" to (s.primary to bg))
                    add("error/surface#$i" to (s.error to bg))
                }
            }
        pairs.forEach { (name, pair) -> assertContrast(name, pair.first, pair.second, TEXT_MIN) }
        assertContrast("outline/surface", s.outline, s.surface, NON_TEXT_MIN)
    }

    private fun assertCycleColours(
        c: CycleColors,
        s: ColorScheme,
    ) {
        listOf(
            "period" to c.period,
            "predictedPeriod" to c.predictedPeriod,
            "ovulation" to c.ovulation,
            "menstrualPhase" to c.menstrualPhase,
            "follicularPhase" to c.follicularPhase,
            "ovulatoryPhase" to c.ovulatoryPhase,
            "lutealPhase" to c.lutealPhase,
        ).forEach { (name, colour) -> assertContrast("$name/surface", colour, s.surface, NON_TEXT_MIN) }
        assertContrast("onPeriod/period", c.onPeriod, c.period, TEXT_MIN)
        assertContrast("onPeriodContainer/periodContainer", c.onPeriodContainer, c.periodContainer, TEXT_MIN)
        assertContrast("onFertileWindow/fertileWindow", c.onFertileWindow, c.fertileWindow, TEXT_MIN)
    }

    private fun assertContrast(
        name: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= minimum, "$name contrast is ${"%.2f".format(ratio)}:1, needs $minimum:1")
    }

    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** WCAG 2.1 relative luminance of an sRGB colour. */
    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private companion object {
        const val TEXT_MIN = 4.5
        const val NON_TEXT_MIN = 3.0
    }
}
