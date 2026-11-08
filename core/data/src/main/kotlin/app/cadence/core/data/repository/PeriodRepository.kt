package app.cadence.core.data.repository

import app.cadence.core.domain.model.MenstrualFlow
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface PeriodRepository {
    /** Every day with flow logged. */
    val flowByDay: Flow<Map<LocalDate, MenstrualFlow>>

    /**
     * Logs medium flow on each of [days] that has no flow yet; days that already have
     * flow keep it. Returns the days that were newly logged, for undo.
     */
    suspend fun logPeriodDays(days: Collection<LocalDate>): List<LocalDate>

    /** Removes flow from [days]. */
    suspend fun clearPeriodDays(days: Collection<LocalDate>)
}
