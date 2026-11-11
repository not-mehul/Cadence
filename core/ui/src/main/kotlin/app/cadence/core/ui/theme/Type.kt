package app.cadence.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.cadence.core.ui.R

/**
 * Lora, Cadence's display typeface: a warm serif with Latin and Cyrillic, used for
 * headlines and the big numbers. One variable font file (SIL Open Font License, see
 * assets/licenses) covers every weight.
 */
@OptIn(ExperimentalTextApi::class)
val DisplayFontFamily: FontFamily =
    FontFamily(
        listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
            Font(
                resId = R.font.lora,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

private val Base = Typography()

/**
 * Material 3 type scale. Display, headline and large-title styles are set in
 * [DisplayFontFamily]; everything you read at length or tap stays in the system font,
 * which respects the user's own font choice.
 */
internal val CadenceTypography =
    Typography(
        displayLarge = Base.displayLarge.display(FontWeight.SemiBold),
        displayMedium = Base.displayMedium.display(FontWeight.SemiBold),
        displaySmall = Base.displaySmall.display(FontWeight.SemiBold),
        headlineLarge = Base.headlineLarge.display(FontWeight.SemiBold),
        headlineMedium = Base.headlineMedium.display(FontWeight.SemiBold),
        headlineSmall = Base.headlineSmall.display(FontWeight.SemiBold),
        titleLarge = Base.titleLarge.display(FontWeight.SemiBold),
        titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        bodyLarge = Base.bodyLarge,
        bodyMedium = Base.bodyMedium,
        bodySmall = Base.bodySmall,
        labelLarge = Base.labelLarge,
        labelMedium = Base.labelMedium,
        labelSmall = Base.labelSmall,
    )

private fun androidx.compose.ui.text.TextStyle.display(weight: FontWeight) =
    copy(fontFamily = DisplayFontFamily, fontWeight = weight, letterSpacing = 0.sp)
