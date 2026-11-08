package app.cadence.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

internal val CadenceLightColorScheme: ColorScheme =
    with(Palette) {
        lightColorScheme(
            primary = RosewoodLight,
            onPrimary = OnRosewoodLight,
            primaryContainer = RosewoodContainerLight,
            onPrimaryContainer = OnRosewoodContainerLight,
            inversePrimary = InversePrimaryLight,
            secondary = MauveLight,
            onSecondary = OnMauveLight,
            secondaryContainer = MauveContainerLight,
            onSecondaryContainer = OnMauveContainerLight,
            tertiary = TealLight,
            onTertiary = OnTealLight,
            tertiaryContainer = TealContainerLight,
            onTertiaryContainer = OnTealContainerLight,
            error = ErrorLight,
            onError = OnErrorLight,
            errorContainer = ErrorContainerLight,
            onErrorContainer = OnErrorContainerLight,
            background = SurfaceLight,
            onBackground = OnSurfaceLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = OnSurfaceVariantLight,
            surfaceTint = RosewoodLight,
            inverseSurface = InverseSurfaceLight,
            inverseOnSurface = InverseOnSurfaceLight,
            outline = OutlineLight,
            outlineVariant = OutlineVariantLight,
            surfaceBright = SurfaceBrightLight,
            surfaceDim = SurfaceDimLight,
            surfaceContainerLowest = SurfaceContainerLowestLight,
            surfaceContainerLow = SurfaceContainerLowLight,
            surfaceContainer = SurfaceContainerLight,
            surfaceContainerHigh = SurfaceContainerHighLight,
            surfaceContainerHighest = SurfaceContainerHighestLight,
        )
    }

internal val CadenceDarkColorScheme: ColorScheme =
    with(Palette) {
        darkColorScheme(
            primary = RosewoodDark,
            onPrimary = OnRosewoodDark,
            primaryContainer = RosewoodContainerDark,
            onPrimaryContainer = OnRosewoodContainerDark,
            inversePrimary = InversePrimaryDark,
            secondary = MauveDark,
            onSecondary = OnMauveDark,
            secondaryContainer = MauveContainerDark,
            onSecondaryContainer = OnMauveContainerDark,
            tertiary = TealDark,
            onTertiary = OnTealDark,
            tertiaryContainer = TealContainerDark,
            onTertiaryContainer = OnTealContainerDark,
            error = ErrorDark,
            onError = OnErrorDark,
            errorContainer = ErrorContainerDark,
            onErrorContainer = OnErrorContainerDark,
            background = SurfaceDark,
            onBackground = OnSurfaceDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = OnSurfaceVariantDark,
            surfaceTint = RosewoodDark,
            inverseSurface = InverseSurfaceDark,
            inverseOnSurface = InverseOnSurfaceDark,
            outline = OutlineDark,
            outlineVariant = OutlineVariantDark,
            surfaceBright = SurfaceBrightDark,
            surfaceDim = SurfaceDimDark,
            surfaceContainerLowest = SurfaceContainerLowestDark,
            surfaceContainerLow = SurfaceContainerLowDark,
            surfaceContainer = SurfaceContainerDark,
            surfaceContainerHigh = SurfaceContainerHighDark,
            surfaceContainerHighest = SurfaceContainerHighestDark,
        )
    }
