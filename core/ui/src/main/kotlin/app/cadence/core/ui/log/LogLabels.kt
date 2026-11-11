package app.cadence.core.ui.log

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cadence.core.domain.log.CustomTag
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

    /** For non-Compose callers such as the PDF report. Null for an unknown id. */
    @StringRes
    fun symptomRes(id: String): Int? = symptoms[id]

    @Composable
    fun mood(id: String): String = moods[id]?.let { stringResource(it) } ?: id

    /** A symptom, mood or custom tag by its pattern item id (see [app.cadence.core.domain.log.DayEntry.patternItems]). */
    @Composable
    fun item(
        id: String,
        tagLabels: Map<Long, String>,
    ): String = CustomTag.fromItemId(id)?.let { tagLabels[it] } ?: itemRes(id)?.let { stringResource(it) } ?: id

    /** For non-Compose callers: the built-in symptom or mood label, or null (a custom tag or unknown id). */
    @StringRes
    fun itemRes(id: String): Int? = symptoms[id] ?: moods[id]

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
            LogCategory.TAGS -> R.string.category_tags
            LogCategory.MEDICATIONS -> R.string.category_medications
        }

    /**
     * Short items summarising a day, in log-sheet order, each with its icon. Used on Today and the
     * calendar. [tagLabels] names custom tags; [includeNote] adds a "Note" item when there is one.
     */
    @Composable
    fun summary(
        entry: DayEntry,
        tagLabels: Map<Long, String> = emptyMap(),
        includeNote: Boolean = true,
    ): List<LogSummaryItem> =
        buildList {
            entry.flow?.let {
                add(LogSummaryItem(stringResource(R.string.flow_summary, stringResource(flow(it))), flow = it))
            }
            LogCatalog.symptoms.filter { it in entry.symptoms }.forEach { id ->
                val severity = stringResource(severity(entry.symptoms.getValue(id)))
                add(
                    LogSummaryItem(
                        stringResource(R.string.symptom_with_severity, symptom(id), severity),
                        LogIcons.symptom(id),
                    ),
                )
            }
            LogCatalog.moods.filter { it in entry.moods }.forEach { add(LogSummaryItem(mood(it), LogIcons.mood(it))) }
            val tagIcon = LogIcons.category(LogCategory.TAGS)
            entry.tags
                .mapNotNull(tagLabels::get)
                .sortedBy { it.lowercase() }
                .forEach { add(LogSummaryItem(it, tagIcon)) }
            entry.painLevel?.let {
                add(LogSummaryItem(stringResource(R.string.summary_pain, it), LogIcons.category(LogCategory.PAIN)))
            }
            listOf(
                Triple(entry.energy, LogCategory.ENERGY, R.string.category_energy),
                Triple(entry.sleep, LogCategory.SLEEP, R.string.category_sleep),
                Triple(entry.stress, LogCategory.STRESS, R.string.category_stress),
            ).forEach { (value, category, name) ->
                value?.let {
                    add(
                        LogSummaryItem(
                            stringResource(R.string.summary_rating, stringResource(name), it),
                            LogIcons.category(category),
                        ),
                    )
                }
            }
            entry.sex?.let { sex ->
                val label =
                    when (sex) {
                        SexActivity.NONE -> R.string.sex_none
                        SexActivity.PROTECTED -> R.string.sex_summary_protected
                        SexActivity.UNPROTECTED -> R.string.sex_summary_unprotected
                    }
                add(LogSummaryItem(stringResource(label), LogIcons.sex(sex)))
            }
            if (includeNote && !entry.note.isNullOrBlank()) {
                add(LogSummaryItem(stringResource(R.string.summary_note), LogIcons.category(LogCategory.NOTES)))
            }
        }
}

/** One thing logged on a day. A [flow] item shows a drop filled to that level instead of [icon]. */
data class LogSummaryItem(
    val label: String,
    @param:DrawableRes val icon: Int = R.drawable.ms_water_drop,
    val flow: MenstrualFlow? = null,
)
