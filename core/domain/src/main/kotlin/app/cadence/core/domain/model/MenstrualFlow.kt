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
     * or lengthens a period.
     */
    val countsAsPeriod: Boolean
        get() = this != SPOTTING
}
