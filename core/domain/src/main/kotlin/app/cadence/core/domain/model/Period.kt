package app.cadence.core.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A run of period days, both ends inclusive. Derived from day logs, never stored. */
data class Period(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "Period end $end is before start $start" }
    }

    /** Number of days, counting both ends. */
    val length: Int
        get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun days(): List<LocalDate> = (0 until length).map { start.plusDays(it.toLong()) }
}
