package app.cadence.core.data.backup

import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class CsvExportTest {
    @Test
    fun `header, ordering and quoting`() {
        val csv =
            CsvExport.write(
                BackupData(
                    UserSettings(),
                    listOf(
                        DayEntry(LocalDate.of(2026, 3, 3), painLevel = 4, note = "said \"ouch\", then\nslept"),
                        DayEntry(
                            LocalDate.of(2026, 3, 2),
                            flow = MenstrualFlow.HEAVY,
                            symptoms =
                                mapOf("headache" to Severity.MILD, "acne" to Severity.SEVERE),
                            moods = setOf("sad", "calm"),
                        ),
                        DayEntry(LocalDate.of(2026, 3, 4)),
                    ),
                    emptySet(),
                ),
            )

        assertEquals(
            "date,flow,symptoms,moods,pain,energy,sleep,stress,sex,note,tags,birth_control,medications\r\n" +
                "2026-03-02,heavy,acne:severe;headache:mild,calm;sad,,,,,,,,,\r\n" +
                "2026-03-03,,,,4,,,,,\"said \"\"ouch\"\", then\nslept\",,,\r\n",
            csv,
        )
    }

    @Test
    fun `tags, birth control and medications by name, including days with only a dose`() {
        val csv =
            CsvExport.write(
                BackupData(
                    UserSettings(),
                    listOf(DayEntry(LocalDate.of(2026, 3, 2), tags = setOf(2, 1))),
                    emptySet(),
                    contraceptiveDoses =
                        mapOf(
                            LocalDate.of(2026, 3, 2) to DoseStatus.TAKEN,
                            LocalDate.of(2026, 3, 3) to DoseStatus.MISSED,
                        ),
                    medications =
                        listOf(
                            Medication(5, "Iron", MedicationUse.DAILY),
                            Medication(6, "Ibuprofen", MedicationUse.AS_NEEDED),
                        ),
                    medicationDoses = mapOf(LocalDate.of(2026, 3, 4) to setOf(5L, 6L)),
                    customTags = listOf(CustomTag(1, "Travel"), CustomTag(2, "Gym")),
                ),
            )

        assertEquals(
            listOf(
                "2026-03-02,,,,,,,,,,Gym;Travel,taken,",
                "2026-03-03,,,,,,,,,,,missed,",
                "2026-03-04,,,,,,,,,,,,Ibuprofen;Iron",
            ),
            csv.split("\r\n").drop(1).filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `text that a spreadsheet would run as a formula is kept as text`() {
        assertEquals("'=HYPERLINK(\"x\")", CsvExport.defuse("=HYPERLINK(\"x\")"))
        assertEquals("'+1 cramps", CsvExport.defuse("+1 cramps"))
        assertEquals("'-rest", CsvExport.defuse("-rest"))
        assertEquals("'@home", CsvExport.defuse("@home"))
        assertEquals("'\tTab", CsvExport.defuse("\tTab"))
        assertEquals("-3", CsvExport.defuse("-3"), "numbers stay numbers")
        assertEquals("ok = fine", CsvExport.defuse("ok = fine"))
        assertEquals("", CsvExport.defuse(""))

        val csv =
            CsvExport.write(
                BackupData(UserSettings(), listOf(DayEntry(LocalDate.of(2026, 3, 3), note = "=1+1")), emptySet()),
            )
        assertEquals("2026-03-03,,,,,,,,,'=1+1,,,", csv.lines()[1].trimEnd('\r'))
    }
}
