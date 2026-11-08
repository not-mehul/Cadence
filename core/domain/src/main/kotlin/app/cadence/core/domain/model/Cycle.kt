package app.cadence.core.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A completed cycle: from one period's first day to the day before the next period starts. */
data class Cycle(
    val start: LocalDate,
    val nextStart: LocalDate,
    /** The user marked this cycle as unusual (illness, travel...), so predictions and stats skip it. */
    val excluded: Boolean = false,
) {
    init {
        require(nextStart.isAfter(start)) { "Cycle must end after it starts" }
    }

    val length: Int
        get() = ChronoUnit.DAYS.between(start, nextStart).toInt()
}
