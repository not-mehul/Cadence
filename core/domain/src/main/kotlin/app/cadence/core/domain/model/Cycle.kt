package app.cadence.core.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A completed cycle: from one period's first day to the day before the next period starts. */
data class Cycle(
    val start: LocalDate,
    val nextStart: LocalDate,
) {
    init {
        require(nextStart.isAfter(start)) { "Cycle must end after it starts" }
    }

    val length: Int
        get() = ChronoUnit.DAYS.between(start, nextStart).toInt()
}
