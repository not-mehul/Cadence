package app.cadence.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.medication.MedicationUse
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

// Phase 6 (schema v7): birth control doses, medications, and custom tags.

/** What the user recorded for the day's pill, patch or ring. */
@Entity(tableName = "contraceptive_dose")
data class ContraceptiveDoseEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: LocalDate,
    @ColumnInfo(name = "status")
    val status: DoseStatus,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Instant,
)

@Entity(tableName = "medication")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "usage")
    val use: MedicationUse,
    /** Minutes after midnight, or null for no reminder. */
    @ColumnInfo(name = "reminder_minute")
    val reminderMinute: Int? = null,
    @ColumnInfo(name = "active")
    val active: Boolean = true,
)

/** A medication taken on a day. Deleting the medication deletes its doses. */
@Entity(
    tableName = "medication_dose",
    primaryKeys = ["date", "medication_id"],
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medication_id")],
)
data class MedicationDoseEntity(
    @ColumnInfo(name = "date")
    val date: LocalDate,
    @ColumnInfo(name = "medication_id")
    val medicationId: Long,
)

/** A tag the user made up. Where it's logged lives in day_tag (kind CUSTOM). */
@Entity(tableName = "custom_tag")
data class CustomTagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "label")
    val label: String,
)

@Dao
interface ContraceptiveDoseDao {
    @Query("SELECT * FROM contraceptive_dose")
    fun observeAll(): Flow<List<ContraceptiveDoseEntity>>

    @Query("SELECT * FROM contraceptive_dose ORDER BY date")
    suspend fun getAll(): List<ContraceptiveDoseEntity>

    @Upsert
    suspend fun upsert(dose: ContraceptiveDoseEntity)

    @Upsert
    suspend fun upsertAll(doses: List<ContraceptiveDoseEntity>)

    @Query("DELETE FROM contraceptive_dose WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM contraceptive_dose")
    suspend fun deleteAll()
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication ORDER BY id")
    suspend fun getAll(): List<MedicationEntity>

    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("DELETE FROM medication WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM medication")
    suspend fun deleteAll()

    @Query("SELECT * FROM medication_dose")
    fun observeDoses(): Flow<List<MedicationDoseEntity>>

    @Query("SELECT * FROM medication_dose ORDER BY date, medication_id")
    suspend fun getAllDoses(): List<MedicationDoseEntity>

    @Upsert
    suspend fun upsertDoses(doses: List<MedicationDoseEntity>)

    @Query("DELETE FROM medication_dose WHERE medication_id = :medicationId")
    suspend fun deleteDosesFor(medicationId: Long)

    @Query("DELETE FROM medication_dose")
    suspend fun deleteAllDoses()

    @Query("DELETE FROM medication_dose WHERE date = :date AND medication_id = :medicationId")
    suspend fun deleteDose(
        date: LocalDate,
        medicationId: Long,
    )
}

@Dao
interface CustomTagDao {
    @Query("SELECT * FROM custom_tag ORDER BY label COLLATE NOCASE, id")
    fun observeAll(): Flow<List<CustomTagEntity>>

    @Query("SELECT * FROM custom_tag ORDER BY id")
    suspend fun getAll(): List<CustomTagEntity>

    @Insert
    suspend fun insert(tag: CustomTagEntity): Long

    @Update
    suspend fun update(tag: CustomTagEntity)

    @Query("DELETE FROM custom_tag WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM custom_tag")
    suspend fun deleteAll()
}
