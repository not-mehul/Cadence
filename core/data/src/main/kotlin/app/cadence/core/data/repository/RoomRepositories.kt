package app.cadence.core.data.repository

import androidx.room.withTransaction
import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.DayLogEntity
import app.cadence.core.data.db.SettingsEntity
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
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
import javax.inject.Inject

/**
 * The database is injected lazily and first touched on a background thread, because
 * opening it unwraps the key from the Keystore.
 */
private fun <T> Lazy<CadenceDatabase>.observe(query: (CadenceDatabase) -> Flow<T>): Flow<T> =
    flow { emitAll(query(get())) }.flowOn(Dispatchers.IO)

private suspend fun <T> Lazy<CadenceDatabase>.write(block: suspend (CadenceDatabase) -> T): T =
    withContext(Dispatchers.IO) { get().let { db -> db.withTransaction { block(db) } } }

class RoomPeriodRepository
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
        private val clock: Clock,
    ) : PeriodRepository {
        override val flowByDay: Flow<Map<LocalDate, MenstrualFlow>> =
            database
                .observe { it.dayLogDao().observeFlowDays() }
                .map { days -> days.associate { it.date to it.flow } }

        override suspend fun logPeriodDays(days: Collection<LocalDate>): List<LocalDate> =
            database.write { db ->
                val dao = db.dayLogDao()
                val existing = dao.getAll(days.toList()).associateBy { it.date }
                val now = Instant.now(clock)
                val added = days.distinct().filter { existing[it]?.flow == null }.sorted()
                dao.upsertAll(
                    added.map { date ->
                        existing[date]?.copy(flow = MenstrualFlow.MEDIUM, updatedAt = now)
                            ?: DayLogEntity(date = date, flow = MenstrualFlow.MEDIUM, updatedAt = now)
                    },
                )
                added
            }

        override suspend fun clearPeriodDays(days: Collection<LocalDate>) {
            if (days.isEmpty()) return
            database.write { db ->
                val dates = days.toList()
                db.dayLogDao().clearFlow(dates, Instant.now(clock))
                db.dayLogDao().deleteEmpty(dates)
            }
        }
    }

class RoomSettingsRepository
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
    ) : SettingsRepository {
        override val settings: Flow<UserSettings> =
            database
                .observe { it.settingsDao().observe() }
                .map { it?.toModel() ?: UserSettings() }

        override suspend fun update(transform: (UserSettings) -> UserSettings) {
            database.write { db ->
                val current = db.settingsDao().get()?.toModel() ?: UserSettings()
                db.settingsDao().upsert(SettingsEntity.from(transform(current)))
            }
        }
    }

class RoomDataEraser
    @Inject
    constructor(
        private val database: Lazy<CadenceDatabase>,
    ) : DataEraser {
        override suspend fun eraseAll() =
            withContext(Dispatchers.IO) {
                val db = database.get()
                db.clearAllTables()
                // Fold the write-ahead log back in and rebuild the file, so erased rows don't linger on disk.
                db.openHelper.writableDatabase.run {
                    query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                    execSQL("VACUUM")
                }
            }
    }
