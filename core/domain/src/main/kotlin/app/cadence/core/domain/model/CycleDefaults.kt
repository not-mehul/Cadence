package app.cadence.core.domain.model

/**
 * The user's typical cycle and period length. Used for predictions until there is
 * enough logged history to learn from.
 */
data class CycleDefaults(
    val cycleLength: Int = DEFAULT_CYCLE_LENGTH,
    val periodLength: Int = DEFAULT_PERIOD_LENGTH,
) {
    init {
        require(cycleLength in CYCLE_LENGTH_RANGE) {
            "cycleLength must be in $CYCLE_LENGTH_RANGE, was $cycleLength"
        }
        require(periodLength in PERIOD_LENGTH_RANGE) {
            "periodLength must be in $PERIOD_LENGTH_RANGE, was $periodLength"
        }
        require(periodLength < cycleLength) {
            "periodLength ($periodLength) must be shorter than cycleLength ($cycleLength)"
        }
    }

    companion object {
        const val DEFAULT_CYCLE_LENGTH = 28
        const val DEFAULT_PERIOD_LENGTH = 5

        /** Cycles outside this range are treated as logging gaps, not real cycles. */
        val CYCLE_LENGTH_RANGE = 15..60
        val PERIOD_LENGTH_RANGE = 1..15
    }
}
