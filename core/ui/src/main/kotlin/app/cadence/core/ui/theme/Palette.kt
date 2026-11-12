package app.cadence.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw Cadence palette. Use [CadenceTheme]'s colour scheme or
 * [CycleColors] in UI code rather than these values directly.
 */
internal object Palette {
    // Light
    val RosewoodLight = Color(0xFF9A3B53)
    val OnRosewoodLight = Color(0xFFFFFFFF)
    val RosewoodContainerLight = Color(0xFFFFD9E0)
    val OnRosewoodContainerLight = Color(0xFF3F0018)

    val MauveLight = Color(0xFF6E5A5E)
    val OnMauveLight = Color(0xFFFFFFFF)
    val MauveContainerLight = Color(0xFFF7DCE1)
    val OnMauveContainerLight = Color(0xFF261719)

    val TealLight = Color(0xFF2E6A67)
    val OnTealLight = Color(0xFFFFFFFF)
    val TealContainerLight = Color(0xFFB4EFEA)
    val OnTealContainerLight = Color(0xFF00201F)

    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF410002)

    val SurfaceLight = Color(0xFFFFF8F7)
    val OnSurfaceLight = Color(0xFF22191B)
    val SurfaceVariantLight = Color(0xFFF3DDE0)
    val OnSurfaceVariantLight = Color(0xFF524345)
    val OutlineLight = Color(0xFF847375)
    val OutlineVariantLight = Color(0xFFD6C2C4)
    val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
    val SurfaceContainerLowLight = Color(0xFFFFF0F2)
    val SurfaceContainerLight = Color(0xFFFBEAEC)
    val SurfaceContainerHighLight = Color(0xFFF5E4E6)
    val SurfaceContainerHighestLight = Color(0xFFEFDFE1)
    val SurfaceDimLight = Color(0xFFE7D6D8)
    val SurfaceBrightLight = Color(0xFFFFF8F7)
    val InverseSurfaceLight = Color(0xFF382E30)
    val InverseOnSurfaceLight = Color(0xFFFEEDEF)
    val InversePrimaryLight = Color(0xFFFFB1C1)

    val AmberLight = Color(0xFF8A5100)
    val PlumLight = Color(0xFF6D4C8C)

    // Soft washes behind content, one per phase. Text in onSurface, onSurfaceVariant and the
    // phase's own colour all meet AA on them (see ContrastTest).
    val RoseTintLight = Color(0xFFFFE3E8)
    val AmberTintLight = Color(0xFFFFE9CF)
    val TealTintLight = Color(0xFFD6F2EE)
    val PlumTintLight = Color(0xFFEEE1FA)
    val NeutralTintLight = Color(0xFFF5E4E6)

    // Calendar: logged period days are a pinkish peach, predicted ones a soft pink with a dashed pink edge.
    val PeachLight = Color(0xFFDF6C7C)
    val OnPeachLight = Color(0xFF2E0610)
    val PinkLight = Color(0xFFD6457A)
    val PinkContainerLight = Color(0xFFFFE3EC)

    // The cycle ring on Today: bright, lively versions of the phase colours. The ring is drawn
    // alongside the phase's name and the day number, so it doesn't carry meaning on its own.
    val RoseRingLight = Color(0xFFEC5B7E)
    val AmberRingLight = Color(0xFFFFA726)
    val TealRingLight = Color(0xFF1FB5A6)
    val PlumRingLight = Color(0xFF9C6ADE)

    // Dark
    val PeachDark = Color(0xFFFF9AA5)
    val OnPeachDark = Color(0xFF3D0A14)
    val PinkDark = Color(0xFFFF8FB1)
    val PinkContainerDark = Color(0xFF4A1D2E)

    val RosewoodDark = Color(0xFFFFB1C1)
    val OnRosewoodDark = Color(0xFF5E1128)
    val RosewoodContainerDark = Color(0xFF7C263D)
    val OnRosewoodContainerDark = Color(0xFFFFD9E0)

    val MauveDark = Color(0xFFDAC1C5)
    val OnMauveDark = Color(0xFF3E2C30)
    val MauveContainerDark = Color(0xFF564246)
    val OnMauveContainerDark = Color(0xFFF7DCE1)

    val TealDark = Color(0xFF98D3CE)
    val OnTealDark = Color(0xFF003735)
    val TealContainerDark = Color(0xFF10504E)
    val OnTealContainerDark = Color(0xFFB4EFEA)

    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val SurfaceDark = Color(0xFF191113)
    val OnSurfaceDark = Color(0xFFEFDFE1)
    val SurfaceVariantDark = Color(0xFF524345)
    val OnSurfaceVariantDark = Color(0xFFD6C2C4)
    val OutlineDark = Color(0xFF9F8C8F)
    val OutlineVariantDark = Color(0xFF524345)
    val SurfaceContainerLowestDark = Color(0xFF140C0E)
    val SurfaceContainerLowDark = Color(0xFF22191B)
    val SurfaceContainerDark = Color(0xFF261D1F)
    val SurfaceContainerHighDark = Color(0xFF31282A)
    val SurfaceContainerHighestDark = Color(0xFF3C3235)
    val SurfaceDimDark = Color(0xFF191113)
    val SurfaceBrightDark = Color(0xFF413738)
    val InverseSurfaceDark = Color(0xFFEFDFE1)
    val InverseOnSurfaceDark = Color(0xFF382E30)
    val InversePrimaryDark = Color(0xFF9A3B53)

    val AmberDark = Color(0xFFFFB870)
    val PlumDark = Color(0xFFD7BAF5)

    val RoseTintDark = Color(0xFF4A1D2A)
    val AmberTintDark = Color(0xFF3F2A10)
    val TealTintDark = Color(0xFF123B39)
    val PlumTintDark = Color(0xFF33264A)
    val NeutralTintDark = Color(0xFF31282A)
}
