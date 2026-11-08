package app.cadence.core.data.backup

import androidx.room.withTransaction
import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.ContraceptiveDoseEntity
import app.cadence.core.data.db.CustomTagEntity
import app.cadence.core.data.db.CycleAnnotationEntity
import app.cadence.core.data.db.MedicationDoseEntity
import app.cadence.core.data.db.SettingsEntity
import app.cadence.core.data.repository.DayEntryRows
import app.cadence.core.data.repository.toEntity
import app.cadence.core.data.repository.toModel
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.model.UserSettings
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

enum class ImportMode {
    /** Erase what's on this phone, then restore the backup (including its settings). */
    REPLACE,

    /** Keep what's here; days in the backup overwrite the same days here. Settings are kept. */
    MERGE,
}

interface BackupRepository {
    suspend fun snapshot(): BackupData

    suspend fun restore(
        data: BackupData,
        mode: ImportMode,
    )
}

class RoomBackupRepository
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
        private val clock: Clock,
    ) : BackupRepository {
        override suspend fun snapshot(): BackupData =
            withContext(Dispatchers.IO) {
                val db = database.get()
                db.withTransaction {
                    BackupData(
                        settings = db.settingsDao().get()?.toModel() ?: UserSettings(),
                        entries = DayEntryRows.readAll(db),
                        excludedCycleStarts = db.cycleAnnotationDao().getExcludedStarts().toSet(),
                        contraceptiveDoses = db.contraceptiveDoseDao().getAll().associate { it.date to it.status },
                        medications = db.medicationDao().getAll().map { it.toModel() },
                        medicationDoses =
                            db
                                .medicationDao()
                                .getAllDoses()
                                .groupBy({ it.date }, { it.medicationId })
                                .mapValues { it.value.toSet() },
                        customTags = db.customTagDao().getAll().map { CustomTag(it.id, it.label) },
                    )
                }
            }

        override suspend fun restore(
            data: BackupData,
            mode: ImportMode,
        ) = withContext(Dispatchers.IO) {
            val db = database.get()
            val now = Instant.now(clock)
            db.withTransaction {
                val current = db.settingsDao().get()?.toModel() ?: UserSettings()
                val ids: IdMaps
                if (mode == ImportMode.REPLACE) {
                    db.dayLogDao().deleteAll()
                    db.dayTagDao().deleteAll()
                    db.cycleAnnotationDao().deleteAll()
                    db.contraceptiveDoseDao().deleteAll()
                    db.medicationDao().deleteAllDoses()
                    db.medicationDao().deleteAll()
                    db.customTagDao().deleteAll()
                    // App lock and screenshot blocking belong to this phone, not the backup.
                    db.settingsDao().upsert(
                        SettingsEntity.from(data.settings.copy(security = current.security, onboardingComplete = true)),
                    )
                    // Everything is gone, so the backup's own ids are free to reuse.
                    data.customTags.forEach { db.customTagDao().insert(CustomTagEntity(it.id, it.label)) }
                    data.medications.forEach { db.medicationDao().insert(it.toEntity()) }
                    ids =
                        IdMaps(
                            data.customTags.associate { it.id to it.id },
                            data.medications.associate { it.id to it.id },
                        )
                } else {
                    db.settingsDao().upsert(SettingsEntity.from(current.copy(onboardingComplete = true)))
                    ids = mergeIds(db, data)
                }
                data.entries.forEach { entry ->
                    DayEntryRows.write(db, entry.copy(tags = entry.tags.map(ids.tags::getValue).toSet()), now)
                }
                data.excludedCycleStarts.forEach {
                    db.cycleAnnotationDao().upsert(CycleAnnotationEntity(it, excludeFromPredictions = true))
                }
                db.contraceptiveDoseDao().upsertAll(
                    data.contraceptiveDoses.map { (date, status) -> ContraceptiveDoseEntity(date, status, now) },
                )
                db.medicationDao().upsertDoses(
                    data.medicationDoses.flatMap { (date, meds) ->
                        meds.map { MedicationDoseEntity(date, ids.medications.getValue(it)) }
                    },
                )
            }
        }
    }

/** Backup ids to ids in this database. */
private class IdMaps(
    val tags: Map<Long, Long>,
    val medications: Map<Long, Long>,
)

/**
 * When merging, a tag or medication already here with the same name (ignoring case) is the
 * same one; anything else is added with a new id.
 */
private suspend fun mergeIds(
    db: CadenceDatabase,
    data: BackupData,
): IdMaps {
    val tagDao = db.customTagDao()
    val existingTags = tagDao.getAll().associateBy { it.label.lowercase() }
    val tags =
        data.customTags.associate { tag ->
            tag.id to (existingTags[tag.label.lowercase()]?.id ?: tagDao.insert(CustomTagEntity(label = tag.label)))
        }
    val medicationDao = db.medicationDao()
    val existingMedications = medicationDao.getAll().associateBy { it.name.lowercase() }
    val medications =
        data.medications.associate { medication ->
            medication.id to
                (
                    existingMedications[medication.name.lowercase()]?.id
                        ?: medicationDao.insert(medication.copy(id = 0).toEntity())
                )
        }
    return IdMaps(tags, medications)
}
