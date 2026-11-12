package app.cadence.core.domain.model

/** Phases of the menstrual cycle. */
enum class CyclePhase {
    MENSTRUAL,
    FOLLICULAR,

    /** The estimated fertile window around ovulation. An estimate only. */
    OVULATORY,
    LUTEAL,
}
