package app.cadence.core.domain.log

/**
 * The built-in symptoms and moods. Ids are stored in the database, so they must never
 * change; labels live in the UI's string resources. Custom tags arrive in Phase 6.
 */
object LogCatalog {
    val symptoms: List<String> =
        listOf(
            "cramps",
            "headache",
            "bloating",
            "breast_tenderness",
            "back_pain",
            "acne",
            "fatigue",
            "nausea",
            "cravings",
            "insomnia",
            "dizziness",
            "diarrhea",
            "constipation",
            "hot_flashes",
        )

    val moods: List<String> =
        listOf(
            "happy",
            "calm",
            "energetic",
            "sensitive",
            "irritable",
            "anxious",
            "sad",
            "mood_swings",
        )
}

/** Sections of the daily log. All but [FLOW] can be hidden in settings. */
enum class LogCategory {
    FLOW,
    SYMPTOMS,
    MOOD,
    PAIN,
    ENERGY,
    SLEEP,
    STRESS,
    SEX,
    NOTES,
    ;

    val canBeHidden: Boolean
        get() = this != FLOW

    companion object {
        /** Parses a stored list, ignoring unknown names (e.g. from a newer app version). */
        fun parseSet(stored: String): Set<LogCategory> =
            stored
                .split(',')
                .mapNotNull { name -> entries.firstOrNull { it.name == name } }
                .filter { it.canBeHidden }
                .toSet()

        fun formatSet(categories: Set<LogCategory>): String =
            categories.filter { it.canBeHidden }.sortedBy { it.ordinal }.joinToString(",") { it.name }
    }
}
