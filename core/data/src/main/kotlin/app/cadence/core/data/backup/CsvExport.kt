package app.cadence.core.data.backup

import app.cadence.core.domain.log.DayEntry

/** One row per logged day, for spreadsheets (RFC 4180 quoting). Not importable; use a backup for that. */
object CsvExport {
    val HEADER =
        listOf(
            "date",
            "flow",
            "symptoms",
            "moods",
            "pain",
            "energy",
            "sleep",
            "stress",
            "sex",
            "note",
            "tags",
            "birth_control",
            "medications",
        )

    fun write(data: BackupData): String =
        buildString {
            appendRow(HEADER)
            val tagLabels = data.customTags.associate { it.id to it.label }
            val medicationNames = data.medications.associate { it.id to it.name }
            val byDate = data.entries.filterNot { it.isEmpty }.associateBy { it.date }
            // A day with only a dose recorded still gets a row.
            val dates = (byDate.keys + data.contraceptiveDoses.keys + data.medicationDoses.keys).sorted()
            dates.forEach { date ->
                val e = byDate[date] ?: DayEntry(date)
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
                        e.tags
                            .mapNotNull(tagLabels::get)
                            .sorted()
                            .joinToString(";"),
                        data.contraceptiveDoses[date]
                            ?.name
                            ?.lowercase()
                            .orEmpty(),
                        data.medicationDoses[date]
                            .orEmpty()
                            .mapNotNull(medicationNames::get)
                            .sorted()
                            .joinToString(";"),
                    ),
                )
            }
        }

    private fun StringBuilder.appendRow(fields: List<String>) {
        fields.joinTo(this, ",") { quote(defuse(it)) }
        append("\r\n")
    }

    /**
     * Spreadsheet apps run a cell that starts with = + - @ (or a tab or carriage return) as a
     * formula, so a note like "=HYPERLINK(...)" could do something when the file is opened.
     * Such text gets a leading apostrophe, which spreadsheets show as plain text. Numbers are
     * left alone.
     */
    internal fun defuse(field: String): String =
        if (field.firstOrNull() in FORMULA_STARTS && !field.matches(NUMBER)) "'$field" else field

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
    private val NUMBER = Regex("""[+-]?\d+(\.\d+)?""")

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
