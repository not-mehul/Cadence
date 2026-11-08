package app.cadence.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DayLogEntity::class],
    version = CadenceDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CadenceDatabase : RoomDatabase() {
    abstract fun dayLogDao(): DayLogDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "cadence.db"
    }
}
