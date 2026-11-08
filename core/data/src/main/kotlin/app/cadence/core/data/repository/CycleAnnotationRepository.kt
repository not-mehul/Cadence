package app.cadence.core.data.repository

import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.CycleAnnotationEntity
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/** Cycles the user excluded from predictions and statistics (docs/PLAN.md §5.1). */
interface CycleAnnotationRepository {
    /** First days of excluded cycles. */
    val excludedStarts: Flow<Set<LocalDate>>

    suspend fun setExcluded(
        cycleStart: LocalDate,
        excluded: Boolean,
    )
}

class RoomCycleAnnotationRepository
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
    ) : CycleAnnotationRepository {
        override val excludedStarts: Flow<Set<LocalDate>> =
            flow { emitAll(database.get().cycleAnnotationDao().observeExcludedStarts()) }
                .map { it.toSet() }
                .flowOn(Dispatchers.IO)

        override suspend fun setExcluded(
            cycleStart: LocalDate,
            excluded: Boolean,
        ) = withContext(Dispatchers.IO) {
            val dao = database.get().cycleAnnotationDao()
            if (excluded) {
                dao.upsert(
                    CycleAnnotationEntity(cycleStart, excludeFromPredictions = true),
                )
            } else {
                dao.delete(cycleStart)
            }
        }
    }
