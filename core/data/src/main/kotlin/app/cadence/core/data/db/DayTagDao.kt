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

    @Query("SELECT * FROM day_tag ORDER BY date")
    suspend fun getAll(): List<DayTagEntity>

    @Query("DELETE FROM day_tag")
    suspend fun deleteAll()

    @Query("DELETE FROM day_tag WHERE date = :date")
    suspend fun deleteFor(date: LocalDate)

    /** Removes a tag from every day it was logged (when a custom tag is deleted). */
    @Query("DELETE FROM day_tag WHERE tag_id = :tagId")
    suspend fun deleteTag(tagId: String)

    @Query("SELECT date, tag_id FROM day_tag")
    fun observeAllTagIds(): Flow<List<DateTagId>>

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

/** A tag logged on a day, without its details. */
data class DateTagId(
    @androidx.room.ColumnInfo(name = "date")
    val date: LocalDate,
    @androidx.room.ColumnInfo(name = "tag_id")
    val tagId: String,
)
