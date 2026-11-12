package app.cadence.core.ui.log

import androidx.annotation.DrawableRes
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.LogCatalog
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.log.SexActivity
import app.cadence.core.ui.R

/**
 * Icons for logged values (Material Symbols Rounded). Always shown next to their label or
 * with a content description: an icon alone is a hint, not the meaning.
 */
object LogIcons {
    private val symptoms =
        mapOf(
            "cramps" to R.drawable.ms_gastroenterology,
            "headache" to R.drawable.ms_psychology_alt,
            "bloating" to R.drawable.ms_bubble_chart,
            "breast_tenderness" to R.drawable.ms_healing,
            "back_pain" to R.drawable.ms_orthopedics,
            "acne" to R.drawable.ms_dermatology,
            "fatigue" to R.drawable.ms_battery_low,
            "nausea" to R.drawable.ms_sick,
            "cravings" to R.drawable.ms_icecream,
            "insomnia" to R.drawable.ms_bedtime_off,
            "dizziness" to R.drawable.ms_cyclone,
            "diarrhea" to R.drawable.ms_wc,
            "constipation" to R.drawable.ms_hourglass_empty,
            "hot_flashes" to R.drawable.ms_local_fire_department,
        )

    private val moods =
        mapOf(
            "happy" to R.drawable.ms_sentiment_very_satisfied,
            "calm" to R.drawable.ms_sentiment_calm,
            "energetic" to R.drawable.ms_sentiment_excited,
            "sensitive" to R.drawable.ms_mood_heart,
            "irritable" to R.drawable.ms_sentiment_frustrated,
            "anxious" to R.drawable.ms_sentiment_worried,
            "sad" to R.drawable.ms_sentiment_sad,
            "mood_swings" to R.drawable.ms_contrast,
        )

    /** Every built-in symptom and mood has an icon; unknown ids get a neutral one. */
    @DrawableRes
    fun symptom(id: String): Int = symptoms[id] ?: R.drawable.ms_ecg_heart

    @DrawableRes
    fun mood(id: String): Int = moods[id] ?: R.drawable.ms_mood

    /** A symptom, mood or custom tag by its pattern item id (see [app.cadence.core.domain.log.DayEntry.patternItems]). */
    @DrawableRes
    fun item(id: String): Int =
        symptoms[id] ?: moods[id]
            ?: if (CustomTag.fromItemId(id) != null) R.drawable.ms_sell else R.drawable.ms_ecg_heart

    @DrawableRes
    fun category(category: LogCategory): Int =
        when (category) {
            LogCategory.FLOW -> R.drawable.ms_water_drop
            LogCategory.SYMPTOMS -> R.drawable.ms_ecg_heart
            LogCategory.MOOD -> R.drawable.ms_mood
            LogCategory.PAIN -> R.drawable.ms_sentiment_extremely_dissatisfied
            LogCategory.ENERGY -> R.drawable.ms_bolt
            LogCategory.SLEEP -> R.drawable.ms_bedtime
            LogCategory.STRESS -> R.drawable.ms_stress_management
            LogCategory.SEX -> R.drawable.ms_favorite
            LogCategory.NOTES -> R.drawable.ms_edit_note
            LogCategory.TAGS -> R.drawable.ms_sell
            LogCategory.MEDICATIONS -> R.drawable.ms_medication
        }

    @DrawableRes
    fun sex(sex: SexActivity): Int =
        when (sex) {
            SexActivity.NONE -> R.drawable.ms_block
            SexActivity.PROTECTED -> R.drawable.ms_shield_with_heart
            SexActivity.UNPROTECTED -> R.drawable.ms_favorite
        }

    init {
        check(LogCatalog.symptoms.all { it in symptoms } && LogCatalog.moods.all { it in moods }) {
            "Every built-in symptom and mood needs an icon"
        }
    }
}
