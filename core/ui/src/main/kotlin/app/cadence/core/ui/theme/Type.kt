package app.cadence.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Base = Typography()

/**
 * Material 3 type scale on the system font (no bundled fonts keeps the APK small and
 * respects the user's font choice), with slightly heavier display and title weights
 * for the calm, confident headline style.
 */
internal val CadenceTypography =
    Typography(
        displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Medium),
        displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Medium),
        displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Medium),
        headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Medium),
        headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Medium),
        headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Medium),
        titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = Base.titleSmall,
        bodyLarge = Base.bodyLarge,
        bodyMedium = Base.bodyMedium,
        bodySmall = Base.bodySmall,
        labelLarge = Base.labelLarge,
        labelMedium = Base.labelMedium,
        labelSmall = Base.labelSmall,
    )
