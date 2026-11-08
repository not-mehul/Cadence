package app.cadence.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.cadence.core.domain.model.MenstrualFlow
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Projection of a day that has flow logged. */
data class FlowDay(
    @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "flow") val flow: MenstrualFlow,
)

@Dao
interface DayLogDao {
    @Upsert
    suspend fun upsert(log: DayLogEntity)

    @Upsert
    suspend fun upsertAll(logs: List<DayLogEntity>)

    @Query("SELECT * FROM day_log WHERE date = :date")
    suspend fun get(date: LocalDate): DayLogEntity?

    @Query("SELECT * FROM day_log WHERE date IN (:dates)")
    suspend fun getAll(dates: List<LocalDate>): List<DayLogEntity>

    @Query("SELECT * FROM day_log WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DayLogEntity>>

    @Query("SELECT * FROM day_log ORDER BY date")
    fun observeAll(): Flow<List<DayLogEntity>>

    @Query("SELECT date, flow FROM day_log WHERE flow IS NOT NULL ORDER BY date")
    fun observeFlowDays(): Flow<List<FlowDay>>

    @Query("UPDATE day_log SET flow = NULL, updated_at = :now WHERE date IN (:dates)")
    suspend fun clearFlow(
        dates: List<LocalDate>,
        now: Instant,
    )

    /** Removes rows that no longer hold anything, so cleared days leave no trace. */
    @Query(
        "DELETE FROM day_log WHERE date IN (:dates) AND flow IS NULL AND note IS NULL AND pain_level IS NULL " +
            "AND energy IS NULL AND sleep IS NULL AND stress IS NULL AND sex IS NULL",
    )
    suspend fun deleteEmpty(dates: List<LocalDate>)

    @Query("DELETE FROM day_log WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM day_log")
    suspend fun deleteAll()
}
