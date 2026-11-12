package app.cadence.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DayLogEntity::class,
        SettingsEntity::class,
        DayTagEntity::class,
        CycleAnnotationEntity::class,
        ContraceptiveDoseEntity::class,
        MedicationEntity::class,
        MedicationDoseEntity::class,
        CustomTagEntity::class,
    ],
    version = CadenceDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // v2: adds the settings table.
        AutoMigration(from = 1, to = 2),
        // v3: daily log fields, symptoms and moods, hidden log categories.
        AutoMigration(from = 2, to = 3),
        // v4: cycles excluded from predictions.
        AutoMigration(from = 3, to = 4),
        // v5: reminder settings.
        AutoMigration(from = 4, to = 5),
        // v6: app lock and screenshot blocking.
        AutoMigration(from = 5, to = 6),
        // v7: birth control schedule and doses, medications, custom tags.
        AutoMigration(from = 6, to = 7),
        // v8: packs on hand, for refill reminders.
        AutoMigration(from = 7, to = 8),
    ],
)
@TypeConverters(Converters::class)
abstract class CadenceDatabase : RoomDatabase() {
    abstract fun dayLogDao(): DayLogDao

    abstract fun settingsDao(): SettingsDao

    abstract fun dayTagDao(): DayTagDao

    abstract fun cycleAnnotationDao(): CycleAnnotationDao

    abstract fun contraceptiveDoseDao(): ContraceptiveDoseDao

    abstract fun medicationDao(): MedicationDao

    abstract fun customTagDao(): CustomTagDao

    companion object {
        const val VERSION = 8
        const val FILE_NAME = "cadence.db"
    }
}
