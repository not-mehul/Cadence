package app.cadence.core.ui.format

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date formats built from skeletons, so field order and punctuation follow the user's
 * locale ("Sunday 8 November" vs "Sunday, November 8").
 */
object DateSkeletons {
    /** e.g. "Sunday, November 8" */
    const val WEEKDAY_DAY_MONTH = "EEEEMMMMd"

    /** e.g. "Nov 8" */
    const val DAY_MONTH_SHORT = "MMMd"

    /** e.g. "8 November 2026" */
    const val DAY_MONTH_YEAR = "yMMMMd"

    /** e.g. "November 2026" */
    const val MONTH_YEAR = "yMMMM"
}

fun dateFormatter(
    skeleton: String,
    locale: Locale,
): DateTimeFormatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** The user's primary locale, observed so date formats update when the language changes. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun rememberDateFormatter(skeleton: String): DateTimeFormatter {
    val locale = currentLocale()
    return remember(skeleton, locale) { dateFormatter(skeleton, locale) }
}
