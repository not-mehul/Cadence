package app.cadence.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDate

enum class TagKind { SYMPTOM, MOOD }

/** A symptom or mood logged on a day. Ids come from [app.cadence.core.domain.log.LogCatalog]. */
@Entity(
    tableName = "day_tag",
    primaryKeys = ["date", "tag_id"],
    indices = [Index("date")],
)
data class DayTagEntity(
    @ColumnInfo(name = "date")
    val date: LocalDate,
    @ColumnInfo(name = "tag_id")
    val tagId: String,
    @ColumnInfo(name = "kind")
    val kind: TagKind,
    /** 1 mild, 2 moderate, 3 severe; symptoms only. */
    @ColumnInfo(name = "severity")
    val severity: Int? = null,
)
