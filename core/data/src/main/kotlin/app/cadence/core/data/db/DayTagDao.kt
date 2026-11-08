package app.cadence.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DayTagDao {
    @Query("SELECT * FROM day_tag WHERE date = :date")
    fun observe(date: LocalDate): Flow<List<DayTagEntity>>

    @Insert
    suspend fun insertAll(tags: List<DayTagEntity>)

    @Query("DELETE FROM day_tag WHERE date = :date")
    suspend fun deleteFor(date: LocalDate)

    /** Days in the range with anything logged besides flow: tags, or any other day_log field. */
    @Query(
        "SELECT date FROM day_tag WHERE date BETWEEN :from AND :to " +
            "UNION SELECT date FROM day_log WHERE date BETWEEN :from AND :to AND (note IS NOT NULL " +
            "OR pain_level IS NOT NULL OR energy IS NOT NULL OR sleep IS NOT NULL OR stress IS NOT NULL OR sex IS NOT NULL)",
    )
    fun observeDatesWithDetails(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<LocalDate>>
}
