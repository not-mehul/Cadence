package app.cadence.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CycleAnnotationDao {
    @Query("SELECT cycle_start FROM cycle_annotation WHERE exclude_from_predictions = 1")
    fun observeExcludedStarts(): Flow<List<LocalDate>>

    @Upsert
    suspend fun upsert(annotation: CycleAnnotationEntity)

    @Query("DELETE FROM cycle_annotation WHERE cycle_start = :start")
    suspend fun delete(start: LocalDate)
}
