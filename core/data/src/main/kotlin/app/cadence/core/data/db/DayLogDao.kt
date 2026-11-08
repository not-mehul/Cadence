package app.cadence.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DayLogDao {
    @Upsert
    suspend fun upsert(log: DayLogEntity)

    @Query("SELECT * FROM day_log WHERE date = :date")
    suspend fun get(date: LocalDate): DayLogEntity?

    @Query("SELECT * FROM day_log WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DayLogEntity>>

    @Query("SELECT * FROM day_log ORDER BY date")
    fun observeAll(): Flow<List<DayLogEntity>>

    @Query("DELETE FROM day_log WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM day_log")
    suspend fun deleteAll()
}
