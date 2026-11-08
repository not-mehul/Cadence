package app.cadence.core.data.repository

import app.cadence.core.domain.log.DayEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The full daily log (Phase 2). Flow-only period edits go through [PeriodRepository]. */
interface DayEntryRepository {
    fun observe(date: LocalDate): Flow<DayEntry>

    /** Replaces everything logged for the entry's date. An empty entry removes the day's log. */
    suspend fun save(entry: DayEntry)

    /** Pain level for every day it was logged (for the pain health flag). */
    val painByDay: Flow<Map<LocalDate, Int>>

    /** Days between [from] and [to] (inclusive) with anything logged besides flow. */
    fun observeDatesWithDetails(
        from: LocalDate,
        to: LocalDate,
    ): Flow<Set<LocalDate>>
}
