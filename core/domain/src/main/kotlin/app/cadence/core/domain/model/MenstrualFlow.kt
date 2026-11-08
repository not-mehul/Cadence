package app.cadence.core.domain.model

/** Menstrual flow logged for a single day. */
enum class MenstrualFlow {
    SPOTTING,
    LIGHT,
    MEDIUM,
    HEAVY,
    ;

    /**
     * Whether this flow counts as a period day. Spotting is logged but never starts
     * or lengthens a period (docs/PLAN.md §4).
     */
    val countsAsPeriod: Boolean
        get() = this != SPOTTING
}
