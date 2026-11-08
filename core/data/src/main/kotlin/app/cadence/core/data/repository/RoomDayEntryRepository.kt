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
                emitAll(combine(log, db.dayTagDao().observe(date)) { row, tags -> toEntry(date, row, tags) })
            }.flowOn(Dispatchers.IO)

        override suspend fun save(entry: DayEntry) =
            withContext(Dispatchers.IO) {
                val db = database.get()
                db.withTransaction {
                    val row =
                        DayLogEntity(
                            date = entry.date,
                            flow = entry.flow,
                            note = entry.note,
                            painLevel = entry.painLevel,
                            energy = entry.energy,
                            sleep = entry.sleep,
                            stress = entry.stress,
                            sex = entry.sex,
                            updatedAt = Instant.now(clock),
                        )
                    if (row == DayLogEntity(date = entry.date, updatedAt = row.updatedAt)) {
                        db.dayLogDao().delete(entry.date)
                    } else {
                        db.dayLogDao().upsert(row)
                    }
                    db.dayTagDao().deleteFor(entry.date)
                    db.dayTagDao().insertAll(
                        entry.symptoms.map { (id, severity) ->
                            DayTagEntity(entry.date, id, TagKind.SYMPTOM, severity.ordinal + 1)
                        } + entry.moods.map { DayTagEntity(entry.date, it, TagKind.MOOD) },
                    )
                }
            }

        override fun observeDatesWithDetails(
            from: LocalDate,
            to: LocalDate,
        ): Flow<Set<LocalDate>> =
            flow { emitAll(database.get().dayTagDao().observeDatesWithDetails(from, to)) }
                .map { it.toSet() }
                .flowOn(Dispatchers.IO)

        private fun toEntry(
            date: LocalDate,
            row: DayLogEntity?,
            tags: List<DayTagEntity>,
        ) = DayEntry(
            date = date,
            flow = row?.flow,
            symptoms =
                tags
                    .filter { it.kind == TagKind.SYMPTOM }
                    .associate {
                        it.tagId to
                            Severity.entries[((it.severity ?: 1) - 1).coerceIn(0, Severity.entries.lastIndex)]
                    },
            moods = tags.filter { it.kind == TagKind.MOOD }.map { it.tagId }.toSet(),
            painLevel = row?.painLevel,
            energy = row?.energy,
            sleep = row?.sleep,
            stress = row?.stress,
            sex = row?.sex,
            note = row?.note,
        )
    }
