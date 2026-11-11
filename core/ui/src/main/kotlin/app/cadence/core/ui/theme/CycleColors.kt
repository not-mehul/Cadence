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
    /** Logged period day fill on the calendar (peach). A soft fill: [periodOutline] is the visible edge. */
    val period: Color,
    val onPeriod: Color,
    /** The solid ring around a logged period day; meets 3:1 against the card. */
    val periodOutline: Color,
    /** Predicted period: a dashed outline in this colour (pink)... */
    val predictedPeriod: Color,
    /** ...around this soft pink fill. */
    val predictedPeriodContainer: Color,
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
    /** Soft washes behind content, one per phase; [neutralTint] when no phase applies. */
    val menstrualTint: Color,
    val follicularTint: Color,
    val ovulatoryTint: Color,
    val lutealTint: Color,
    val neutralTint: Color,
)

internal val LightCycleColors =
    with(Palette) {
        CycleColors(
            period = PeachLight,
            onPeriod = OnPeachLight,
            periodOutline = PeachOutlineLight,
            predictedPeriod = PinkLight,
            predictedPeriodContainer = PinkContainerLight,
            periodContainer = RosewoodContainerLight,
            onPeriodContainer = OnRosewoodContainerLight,
            fertileWindow = TealContainerLight,
            onFertileWindow = OnTealContainerLight,
            ovulation = TealLight,
            menstrualPhase = RosewoodLight,
            follicularPhase = AmberLight,
            ovulatoryPhase = TealLight,
            lutealPhase = PlumLight,
            menstrualTint = RoseTintLight,
            follicularTint = AmberTintLight,
            ovulatoryTint = TealTintLight,
            lutealTint = PlumTintLight,
            neutralTint = NeutralTintLight,
        )
    }

internal val DarkCycleColors =
    with(Palette) {
        CycleColors(
            period = PeachDark,
            onPeriod = OnPeachDark,
            periodOutline = PeachDark,
            predictedPeriod = PinkDark,
            predictedPeriodContainer = PinkContainerDark,
            periodContainer = RosewoodContainerDark,
            onPeriodContainer = OnRosewoodContainerDark,
            fertileWindow = TealContainerDark,
            onFertileWindow = OnTealContainerDark,
            ovulation = TealDark,
            menstrualPhase = RosewoodDark,
            follicularPhase = AmberDark,
            ovulatoryPhase = TealDark,
            lutealPhase = PlumDark,
            menstrualTint = RoseTintDark,
            follicularTint = AmberTintDark,
            ovulatoryTint = TealTintDark,
            lutealTint = PlumTintDark,
            neutralTint = NeutralTintDark,
        )
    }

val LocalCycleColors = staticCompositionLocalOf { LightCycleColors }
