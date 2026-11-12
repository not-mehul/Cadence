package app.cadence.core.data.repository

import androidx.room.withTransaction
import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.ContraceptiveDoseEntity
import app.cadence.core.data.db.CustomTagEntity
import app.cadence.core.data.db.MedicationDoseEntity
import app.cadence.core.data.db.MedicationEntity
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.medication.Medication
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** What was recorded for the pill, patch or ring each day. */
interface ContraceptionRepository {
    val doses: Flow<Map<LocalDate, DoseStatus>>

    /** Records [status] for [date]; null clears it. */
    suspend fun record(
        date: LocalDate,
        status: DoseStatus?,
    )
}

/** Medications besides birth control, and the days they were taken. */
interface MedicationRepository {
    /** Alphabetical. */
    val medications: Flow<List<Medication>>

    /** Medication ids taken on each day. */
    val doses: Flow<Map<LocalDate, Set<Long>>>

    /** Adds [medication] when its id is 0, otherwise updates it. Returns its id. */
    suspend fun save(medication: Medication): Long

    /** Deletes the medication and every day it was logged. */
    suspend fun delete(id: Long)

    suspend fun setTaken(
        date: LocalDate,
        medicationId: Long,
        taken: Boolean,
    )
}

/** The user's own tags. Which days they're on is part of [app.cadence.core.domain.log.DayEntry]. */
interface CustomTagRepository {
    /** Alphabetical. */
    val tags: Flow<List<CustomTag>>

    /** Returns the id of the tag with this label, creating it if there's none (ignoring case). */
    suspend fun create(label: String): Long

    suspend fun rename(
        id: Long,
        label: String,
    )

    /** Deletes the tag and removes it from every day. */
    suspend fun delete(id: Long)
}

private fun <T> Lazy<CadenceDatabase>.observe(query: (CadenceDatabase) -> Flow<T>): Flow<T> =
    flow { emitAll(query(get())) }.flowOn(Dispatchers.IO)

private suspend fun <T> Lazy<CadenceDatabase>.write(block: suspend (CadenceDatabase) -> T): T =
    withContext(Dispatchers.IO) { get().let { db -> db.withTransaction { block(db) } } }

internal fun MedicationEntity.toModel() =
    Medication(id, name, use, reminderMinute?.let { LocalTime.of(it / 60, it % 60) }, active)

internal fun Medication.toEntity() =
    MedicationEntity(id, name.trim(), use, reminderTime?.let { it.hour * 60 + it.minute }, active)

class RoomContraceptionRepository
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
        private val clock: Clock,
    ) : ContraceptionRepository {
        override val doses: Flow<Map<LocalDate, DoseStatus>> =
            database.observe { it.contraceptiveDoseDao().observeAll() }.map { rows ->
                rows.associate {
                    it.date to
                        it.status
                }
            }

        override suspend fun record(
            date: LocalDate,
            status: DoseStatus?,
        ) = database.write { db ->
            val dao = db.contraceptiveDoseDao()
            if (status ==
                null
            ) {
                dao.delete(date)
            } else {
                dao.upsert(ContraceptiveDoseEntity(date, status, Instant.now(clock)))
            }
        }
    }

class RoomMedicationRepository
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
    ) : MedicationRepository {
        override val medications: Flow<List<Medication>> =
            database.observe { it.medicationDao().observeAll() }.map { rows -> rows.map { it.toModel() } }

        override val doses: Flow<Map<LocalDate, Set<Long>>> =
            database.observe { it.medicationDao().observeDoses() }.map { rows ->
                rows.groupBy({ it.date }, { it.medicationId }).mapValues { it.value.toSet() }
            }

        override suspend fun save(medication: Medication): Long =
            database.write { db ->
                val dao = db.medicationDao()
                if (medication.id == 0L) {
                    dao.insert(medication.toEntity())
                } else {
                    dao.update(medication.toEntity())
                    medication.id
                }
            }

        override suspend fun delete(id: Long) =
            database.write { db ->
                // Explicit, rather than relying on the foreign key cascade being switched on.
                db.medicationDao().deleteDosesFor(id)
                db.medicationDao().delete(id)
            }

        override suspend fun setTaken(
            date: LocalDate,
            medicationId: Long,
            taken: Boolean,
        ) = database.write { db ->
            val dao = db.medicationDao()
            if (taken) {
                dao.upsertDoses(
                    listOf(MedicationDoseEntity(date, medicationId)),
                )
            } else {
                dao.deleteDose(date, medicationId)
            }
        }
    }

class RoomCustomTagRepository
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
    ) : CustomTagRepository {
        override val tags: Flow<List<CustomTag>> =
            database.observe { it.customTagDao().observeAll() }.map { rows -> rows.map { CustomTag(it.id, it.label) } }

        override suspend fun create(label: String): Long {
            val clean = CustomTag(0, label.trim()).label
            return database.write { db ->
                val dao = db.customTagDao()
                dao.getAll().firstOrNull { it.label.equals(clean, ignoreCase = true) }?.id
                    ?: dao.insert(CustomTagEntity(label = clean))
            }
        }

        override suspend fun rename(
            id: Long,
            label: String,
        ) {
            val clean = CustomTag(id, label.trim()).label
            database.write { it.customTagDao().update(CustomTagEntity(id, clean)) }
        }

        override suspend fun delete(id: Long) =
            database.write { db ->
                db.dayTagDao().deleteTag(CustomTag.itemId(id))
                db.customTagDao().delete(id)
            }
    }
