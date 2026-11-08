package app.cadence.core.domain

import app.cadence.core.domain.model.MenstrualFlow
import java.time.LocalDate

/** Shorthand for test dates: d(2026, 3, 1). */
fun d(
    year: Int,
    month: Int,
    day: Int,
): LocalDate = LocalDate.of(year, month, day)

/** Medium flow on [length] consecutive days from [start]. */
fun periodDays(
    start: LocalDate,
    length: Int,
    flow: MenstrualFlow = MenstrualFlow.MEDIUM,
): Map<LocalDate, MenstrualFlow> = (0 until length).associate { start.plusDays(it.toLong()) to flow }

/** Regular history: [count] periods of [periodLength] days, [cycleLength] days apart, from [firstStart]. */
fun regularHistory(
    firstStart: LocalDate,
    count: Int,
    cycleLength: Int,
    periodLength: Int = 5,
): Map<LocalDate, MenstrualFlow> =
    (0 until count)
        .map { periodDays(firstStart.plusDays(it.toLong() * cycleLength), periodLength) }
        .fold(emptyMap()) { acc, m -> acc + m }
