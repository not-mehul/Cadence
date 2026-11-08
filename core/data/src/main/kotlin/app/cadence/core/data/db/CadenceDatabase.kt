package app.cadence.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DayLogEntity::class, SettingsEntity::class],
    version = CadenceDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // v2 (Phase 1): adds the settings table.
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class CadenceDatabase : RoomDatabase() {
    abstract fun dayLogDao(): DayLogDao

    abstract fun settingsDao(): SettingsDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "cadence.db"
    }
}
