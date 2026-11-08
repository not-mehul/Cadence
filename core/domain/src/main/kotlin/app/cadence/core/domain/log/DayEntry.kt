package app.cadence.core.domain.log

import app.cadence.core.domain.model.MenstrualFlow
import java.time.LocalDate

enum class Severity { MILD, MODERATE, SEVERE }

enum class SexActivity { NONE, PROTECTED, UNPROTECTED }

/**
 * Everything logged for one calendar day (docs/PLAN.md §2.3). Every field is optional;
 * an entry with nothing set means the day has no log.
 */
data class DayEntry(
    val date: LocalDate,
    val flow: MenstrualFlow? = null,
    /** Symptom id (see [LogCatalog.symptoms]) to how bad it was. */
    val symptoms: Map<String, Severity> = emptyMap(),
    /** Mood ids (see [LogCatalog.moods]). */
    val moods: Set<String> = emptySet(),
    val painLevel: Int? = null,
    val energy: Int? = null,
    val sleep: Int? = null,
    val stress: Int? = null,
    val sex: SexActivity? = null,
    val note: String? = null,
) {
    init {
        painLevel?.let { require(it in PAIN_RANGE) { "painLevel must be in $PAIN_RANGE, was $it" } }
        listOf("energy" to energy, "sleep" to sleep, "stress" to stress).forEach { (name, value) ->
            value?.let { require(it in RATING_RANGE) { "$name must be in $RATING_RANGE, was $it" } }
        }
        note?.let { require(it.length <= MAX_NOTE_LENGTH) { "note is longer than $MAX_NOTE_LENGTH characters" } }
    }

    /** True when nothing at all is logged for the day. */
    val isEmpty: Boolean
        get() = this == DayEntry(date)

    /** True when something other than flow is logged; flow already shows as a period day. */
    val hasDetails: Boolean
        get() = copy(flow = null) != DayEntry(date)

    /** One tap on a symptom: off → mild → moderate → severe → off. */
    fun cycleSymptom(id: String): DayEntry {
        val next = SEVERITY_CYCLE[(SEVERITY_CYCLE.indexOf(symptoms[id]) + 1) % SEVERITY_CYCLE.size]
        return copy(symptoms = if (next == null) symptoms - id else symptoms + (id to next))
    }

    fun toggleMood(id: String): DayEntry = copy(moods = if (id in moods) moods - id else moods + id)

    companion object {
        private val SEVERITY_CYCLE = listOf(null) + Severity.entries

        val PAIN_RANGE = 0..10
        val RATING_RANGE = 1..5
        const val MAX_NOTE_LENGTH = 1_000
    }
}
