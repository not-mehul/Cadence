package app.cadence.core.ui.log

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCatalog
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.log.SexActivity
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.ui.R

/** Display names for logged values. Ids come from [LogCatalog]; unknown ids fall back to the id. */
object LogLabels {
    private val symptoms =
        mapOf(
            "cramps" to R.string.symptom_cramps,
            "headache" to R.string.symptom_headache,
            "bloating" to R.string.symptom_bloating,
            "breast_tenderness" to R.string.symptom_breast_tenderness,
            "back_pain" to R.string.symptom_back_pain,
            "acne" to R.string.symptom_acne,
            "fatigue" to R.string.symptom_fatigue,
            "nausea" to R.string.symptom_nausea,
            "cravings" to R.string.symptom_cravings,
            "insomnia" to R.string.symptom_insomnia,
            "dizziness" to R.string.symptom_dizziness,
            "diarrhea" to R.string.symptom_diarrhea,
            "constipation" to R.string.symptom_constipation,
            "hot_flashes" to R.string.symptom_hot_flashes,
        )

    private val moods =
        mapOf(
            "happy" to R.string.mood_happy,
            "calm" to R.string.mood_calm,
            "energetic" to R.string.mood_energetic,
            "sensitive" to R.string.mood_sensitive,
            "irritable" to R.string.mood_irritable,
            "anxious" to R.string.mood_anxious,
            "sad" to R.string.mood_sad,
            "mood_swings" to R.string.mood_mood_swings,
        )

    @Composable
    fun symptom(id: String): String = symptoms[id]?.let { stringResource(it) } ?: id

    @Composable
    fun mood(id: String): String = moods[id]?.let { stringResource(it) } ?: id

    @StringRes
    fun flow(flow: MenstrualFlow?): Int =
        when (flow) {
            null -> R.string.flow_none
            MenstrualFlow.SPOTTING -> R.string.flow_spotting
            MenstrualFlow.LIGHT -> R.string.flow_light
            MenstrualFlow.MEDIUM -> R.string.flow_medium
            MenstrualFlow.HEAVY -> R.string.flow_heavy
        }

    @StringRes
    fun severity(severity: Severity): Int =
        when (severity) {
            Severity.MILD -> R.string.severity_mild
            Severity.MODERATE -> R.string.severity_moderate
            Severity.SEVERE -> R.string.severity_severe
        }

    @StringRes
    fun sex(sex: SexActivity): Int =
        when (sex) {
            SexActivity.NONE -> R.string.sex_none
            SexActivity.PROTECTED -> R.string.sex_protected
            SexActivity.UNPROTECTED -> R.string.sex_unprotected
        }

    @StringRes
    fun category(category: LogCategory): Int =
        when (category) {
            LogCategory.FLOW -> R.string.category_flow
            LogCategory.SYMPTOMS -> R.string.category_symptoms
            LogCategory.MOOD -> R.string.category_mood
            LogCategory.PAIN -> R.string.category_pain
            LogCategory.ENERGY -> R.string.category_energy
            LogCategory.SLEEP -> R.string.category_sleep
            LogCategory.STRESS -> R.string.category_stress
            LogCategory.SEX -> R.string.category_sex
            LogCategory.NOTES -> R.string.category_notes
        }

    /** Short labels summarising a day, in log-sheet order. Used on Today. */
    @Composable
    fun summary(entry: DayEntry): List<String> =
        buildList {
            entry.flow?.let { add(stringResource(R.string.flow_summary, stringResource(flow(it)))) }
            LogCatalog.symptoms.filter { it in entry.symptoms }.forEach { id ->
                val severity = stringResource(severity(entry.symptoms.getValue(id)))
                add(stringResource(R.string.symptom_with_severity, symptom(id), severity))
            }
            LogCatalog.moods.filter { it in entry.moods }.forEach { add(mood(it)) }
            entry.painLevel?.let { add(stringResource(R.string.summary_pain, it)) }
            entry.energy?.let {
                add(
                    stringResource(R.string.summary_rating, stringResource(R.string.category_energy), it),
                )
            }
            entry.sleep?.let {
                add(
                    stringResource(R.string.summary_rating, stringResource(R.string.category_sleep), it),
                )
            }
            entry.stress?.let {
                add(
                    stringResource(R.string.summary_rating, stringResource(R.string.category_stress), it),
                )
            }
            when (entry.sex) {
                SexActivity.NONE -> add(stringResource(R.string.sex_none))
                SexActivity.PROTECTED -> add(stringResource(R.string.sex_summary_protected))
                SexActivity.UNPROTECTED -> add(stringResource(R.string.sex_summary_unprotected))
                null -> Unit
            }
            if (!entry.note.isNullOrBlank()) add(stringResource(R.string.summary_note))
        }
}
