package app.cadence.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours for cycle states. These carry meaning, so they're fixed and never
 * replaced by dynamic colour (docs/PLAN.md §6.1). Every use must also have a
 * non-colour cue such as a label, icon or pattern.
 */
@Immutable
data class CycleColors(
    /** Logged period day fill. */
    val period: Color,
    val onPeriod: Color,
    /** Predicted period: drawn as a dashed outline in this colour. */
    val predictedPeriod: Color,
    /** Soft fill for period days on the calendar. */
    val periodContainer: Color,
    val onPeriodContainer: Color,
    /** Fertile-window tint (estimate only). */
    val fertileWindow: Color,
    val onFertileWindow: Color,
    /** Estimated ovulation marker. */
    val ovulation: Color,
    val menstrualPhase: Color,
    val follicularPhase: Color,
    val ovulatoryPhase: Color,
    val lutealPhase: Color,
)

internal val LightCycleColors =
    with(Palette) {
        CycleColors(
            period = RosewoodLight,
            onPeriod = OnRosewoodLight,
            predictedPeriod = RosewoodLight,
            periodContainer = RosewoodContainerLight,
            onPeriodContainer = OnRosewoodContainerLight,
            fertileWindow = TealContainerLight,
            onFertileWindow = OnTealContainerLight,
            ovulation = TealLight,
            menstrualPhase = RosewoodLight,
            follicularPhase = AmberLight,
            ovulatoryPhase = TealLight,
            lutealPhase = PlumLight,
        )
    }

internal val DarkCycleColors =
    with(Palette) {
        CycleColors(
            period = RosewoodDark,
            onPeriod = OnRosewoodDark,
            predictedPeriod = RosewoodDark,
            periodContainer = RosewoodContainerDark,
            onPeriodContainer = OnRosewoodContainerDark,
            fertileWindow = TealContainerDark,
            onFertileWindow = OnTealContainerDark,
            ovulation = TealDark,
            menstrualPhase = RosewoodDark,
            follicularPhase = AmberDark,
            ovulatoryPhase = TealDark,
            lutealPhase = PlumDark,
        )
    }

val LocalCycleColors = staticCompositionLocalOf { LightCycleColors }
