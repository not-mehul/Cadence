package app.cadence.core.data.backup

import app.cadence.core.domain.log.DayEntry

/** One row per logged day, for spreadsheets (RFC 4180 quoting). Not importable; use a backup for that. */
object CsvExport {
    val HEADER =
        listOf("date", "flow", "symptoms", "moods", "pain", "energy", "sleep", "stress", "sex", "note")

    fun write(entries: List<DayEntry>): String =
        buildString {
            appendRow(HEADER)
            entries.filterNot { it.isEmpty }.sortedBy { it.date }.forEach { e ->
                appendRow(
                    listOf(
                        e.date.toString(),
                        e.flow
                            ?.name
                            ?.lowercase()
                            .orEmpty(),
                        e.symptoms
                            .toSortedMap()
                            .entries
                            .joinToString(";") { "${it.key}:${it.value.name.lowercase()}" },
                        e.moods.sorted().joinToString(";"),
                        e.painLevel?.toString().orEmpty(),
                        e.energy?.toString().orEmpty(),
                        e.sleep?.toString().orEmpty(),
                        e.stress?.toString().orEmpty(),
                        e.sex
                            ?.name
                            ?.lowercase()
                            .orEmpty(),
                        e.note.orEmpty(),
                    ),
                )
            }
        }

    private fun StringBuilder.appendRow(fields: List<String>) {
        fields.joinTo(this, ",") { quote(it) }
        append("\r\n")
    }

    private fun quote(field: String): String =
        if (field.any {
                it == ',' || it == '"' || it == '\n' || it == '\r'
            }
        ) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
