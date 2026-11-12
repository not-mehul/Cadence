package app.cadence.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours for cycle states. These carry meaning, so they're fixed and never
 * replaced by dynamic colour. Every use must also have a
 * non-colour cue such as a label, icon or pattern.
 */
@Immutable
data class CycleColors(
    /** Logged period day fill on the calendar: a pinkish peach that stands out from the card on its own. */
    val period: Color,
    val onPeriod: Color,
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
    /** Estimated ovulation marker: a solid circle, darker than the fertile days around it. */
    val ovulation: Color,
    val onOvulation: Color,
    val menstrualPhase: Color,
    val follicularPhase: Color,
    val ovulatoryPhase: Color,
    val lutealPhase: Color,
    /** The cycle ring's segments: brighter than the phase colours in light mode, the same in dark. */
    val menstrualRing: Color,
    val follicularRing: Color,
    val ovulatoryRing: Color,
    val lutealRing: Color,
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
            predictedPeriod = PinkLight,
            predictedPeriodContainer = PinkContainerLight,
            periodContainer = RosewoodContainerLight,
            onPeriodContainer = OnRosewoodContainerLight,
            fertileWindow = TealContainerLight,
            onFertileWindow = OnTealContainerLight,
            ovulation = TealLight,
            onOvulation = OnTealLight,
            menstrualPhase = RosewoodLight,
            follicularPhase = AmberLight,
            ovulatoryPhase = TealLight,
            lutealPhase = PlumLight,
            menstrualRing = RoseRingLight,
            follicularRing = AmberRingLight,
            ovulatoryRing = TealRingLight,
            lutealRing = PlumRingLight,
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
            predictedPeriod = PinkDark,
            predictedPeriodContainer = PinkContainerDark,
            periodContainer = RosewoodContainerDark,
            onPeriodContainer = OnRosewoodContainerDark,
            fertileWindow = TealContainerDark,
            onFertileWindow = OnTealContainerDark,
            ovulation = TealDark,
            onOvulation = OnTealDark,
            menstrualPhase = RosewoodDark,
            follicularPhase = AmberDark,
            ovulatoryPhase = TealDark,
            lutealPhase = PlumDark,
            menstrualRing = RosewoodDark,
            follicularRing = AmberDark,
            ovulatoryRing = TealDark,
            lutealRing = PlumDark,
            menstrualTint = RoseTintDark,
            follicularTint = AmberTintDark,
            ovulatoryTint = TealTintDark,
            lutealTint = PlumTintDark,
            neutralTint = NeutralTintDark,
        )
    }

val LocalCycleColors = staticCompositionLocalOf { LightCycleColors }
