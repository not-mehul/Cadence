package app.cadence.core.data.repository

import androidx.room.withTransaction
import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.DayLogEntity
import app.cadence.core.data.db.DayTagEntity
import app.cadence.core.data.db.TagKind
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class RoomDayEntryRepository
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
        private val clock: Clock,
    ) : DayEntryRepository {
        override fun observe(date: LocalDate): Flow<DayEntry> =
            flow {
                val db = database.get()
                val log = db.dayLogDao().observeRange(date, date).map { it.firstOrNull() }
                emitAll(
                    combine(log, db.dayTagDao().observe(date)) { row, tags -> DayEntryRows.toEntry(date, row, tags) },
                )
            }.flowOn(Dispatchers.IO)

        override suspend fun save(entry: DayEntry) =
            withContext(Dispatchers.IO) {
                val db = database.get()
                db.withTransaction { DayEntryRows.write(db, entry, Instant.now(clock)) }
            }

        override val painByDay: Flow<Map<LocalDate, Int>> =
            flow { emitAll(database.get().dayLogDao().observePainLevels()) }
                .map { days -> days.associate { it.date to it.painLevel } }
                .flowOn(Dispatchers.IO)

        // Symptom, mood and custom tag ids are all distinct (custom ones start with "tag:"), so the
        // stored tag ids are exactly the pattern item ids.
        override val patternItemsByDay: Flow<Map<LocalDate, Set<String>>> =
            flow { emitAll(database.get().dayTagDao().observeAllTagIds()) }
                .map { rows -> rows.groupBy({ it.date }, { it.tagId }).mapValues { it.value.toSet() } }
                .flowOn(Dispatchers.IO)

        override fun observeDatesWithDetails(
            from: LocalDate,
            to: LocalDate,
        ): Flow<Set<LocalDate>> =
            flow { emitAll(database.get().dayTagDao().observeDatesWithDetails(from, to)) }
                .map { it.toSet() }
                .flowOn(Dispatchers.IO)
    }
