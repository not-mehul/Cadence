package app.cadence.core.data.repository

import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.DayLogEntity
import app.cadence.core.data.db.DayTagEntity
import app.cadence.core.data.db.TagKind
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import java.time.Instant
import java.time.LocalDate

/** Conversions between [DayEntry] and its database rows, shared by saving, backup and restore. */
internal object DayEntryRows {
    fun toEntry(
        date: LocalDate,
        row: DayLogEntity?,
        tags: List<DayTagEntity>,
    ) = DayEntry(
        date = date,
        flow = row?.flow,
        symptoms =
            tags
                .filter { it.kind == TagKind.SYMPTOM }
                .associate {
                    it.tagId to
                        Severity.entries[((it.severity ?: 1) - 1).coerceIn(0, Severity.entries.lastIndex)]
                },
        moods = tags.filter { it.kind == TagKind.MOOD }.map { it.tagId }.toSet(),
        tags = tags.filter { it.kind == TagKind.CUSTOM }.mapNotNull { CustomTag.fromItemId(it.tagId) }.toSet(),
        painLevel = row?.painLevel,
        energy = row?.energy,
        sleep = row?.sleep,
        stress = row?.stress,
        sex = row?.sex,
        note = row?.note,
    )

    /** Replaces everything stored for the entry's date. Call inside a transaction. */
    suspend fun write(
        db: CadenceDatabase,
        entry: DayEntry,
        now: Instant,
    ) {
        val row =
            DayLogEntity(
                date = entry.date,
                flow = entry.flow,
                note = entry.note,
                painLevel = entry.painLevel,
                energy = entry.energy,
                sleep = entry.sleep,
                stress = entry.stress,
                sex = entry.sex,
                updatedAt = now,
            )
        if (row ==
            DayLogEntity(date = entry.date, updatedAt = now)
        ) {
            db.dayLogDao().delete(entry.date)
        } else {
            db.dayLogDao().upsert(row)
        }
        db.dayTagDao().deleteFor(entry.date)
        db.dayTagDao().insertAll(
            entry.symptoms.map { (id, severity) ->
                DayTagEntity(entry.date, id, TagKind.SYMPTOM, severity.ordinal + 1)
            } +
                entry.moods.map { DayTagEntity(entry.date, it, TagKind.MOOD) } +
                entry.tags.map { DayTagEntity(entry.date, CustomTag.itemId(it), TagKind.CUSTOM) },
        )
    }

    /** Every logged day, oldest first. */
    suspend fun readAll(db: CadenceDatabase): List<DayEntry> {
        val rows = db.dayLogDao().getAllRows().associateBy { it.date }
        val tags = db.dayTagDao().getAll().groupBy { it.date }
        return (rows.keys + tags.keys).sorted().map { toEntry(it, rows[it], tags[it].orEmpty()) }
    }
}
