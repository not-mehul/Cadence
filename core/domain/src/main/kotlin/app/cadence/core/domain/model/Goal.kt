package app.cadence.core.domain.model

/**
 * The two modes Cadence supports. Trying to conceive, pregnancy and perimenopause
 * modes are out of scope by decision.
 */
enum class Goal {
    /** Default: track periods and see cycle predictions, including estimated ovulation. */
    TRACK,

    /** Hormonal birth control: ovulation and fertile-window estimates don't apply, so they're hidden. */
    BIRTH_CONTROL,
    ;

    val showsOvulationEstimates: Boolean
        get() = this == TRACK
}
