package app.cadence.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A note on one cycle, keyed by the first day of its period. Only rows for excluded
 * cycles are stored. If that period's start is later edited, the annotation no longer
 * matches any cycle and is simply ignored.
 */
@Entity(tableName = "cycle_annotation")
data class CycleAnnotationEntity(
    @PrimaryKey
    @ColumnInfo(name = "cycle_start")
    val cycleStart: LocalDate,
    @ColumnInfo(name = "exclude_from_predictions")
    val excludeFromPredictions: Boolean,
)
