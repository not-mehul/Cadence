package app.cadence.core.data.backup

import androidx.room.withTransaction
import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.CycleAnnotationEntity
import app.cadence.core.data.db.SettingsEntity
import app.cadence.core.data.repository.DayEntryRows
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
                if (mode == ImportMode.REPLACE) {
                    db.dayLogDao().deleteAll()
                    db.dayTagDao().deleteAll()
                    db.cycleAnnotationDao().deleteAll()
                    // App lock and screenshot blocking belong to this phone, not the backup.
                    db.settingsDao().upsert(
                        SettingsEntity.from(data.settings.copy(security = current.security, onboardingComplete = true)),
                    )
                } else {
                    db.settingsDao().upsert(SettingsEntity.from(current.copy(onboardingComplete = true)))
                }
                data.entries.forEach { DayEntryRows.write(db, it, now) }
                data.excludedCycleStarts.forEach {
                    db.cycleAnnotationDao().upsert(CycleAnnotationEntity(it, excludeFromPredictions = true))
                }
            }
        }
    }
