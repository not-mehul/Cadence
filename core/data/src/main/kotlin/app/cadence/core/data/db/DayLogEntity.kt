package app.cadence.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.cadence.core.domain.log.SexActivity
import app.cadence.core.domain.model.MenstrualFlow
import java.time.Instant
import java.time.LocalDate

/**
 * One row per calendar day the user logged something. This is the source of truth;
 * periods and cycles are derived from it (docs/PLAN.md §4). Symptoms and moods are in
 * [DayTagEntity].
 */
@Entity(tableName = "day_log")
data class DayLogEntity(
    /** Stored as `LocalDate.toEpochDay()`; a calendar day, never an instant. */
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: LocalDate,
    @ColumnInfo(name = "flow")
    val flow: MenstrualFlow? = null,
    @ColumnInfo(name = "note")
    val note: String? = null,
    /** 0–10. Added in schema v3, as are the fields below. */
    @ColumnInfo(name = "pain_level")
    val painLevel: Int? = null,
    /** 1–5. */
    @ColumnInfo(name = "energy")
    val energy: Int? = null,
    /** 1–5. */
    @ColumnInfo(name = "sleep")
    val sleep: Int? = null,
    /** 1–5. */
    @ColumnInfo(name = "stress")
    val stress: Int? = null,
    @ColumnInfo(name = "sex")
    val sex: SexActivity? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
